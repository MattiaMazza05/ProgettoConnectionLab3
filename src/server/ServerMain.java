package server;

import common.GameManager;
import common.MessageReader;
import common.PuzzleRepository;
import common.ServerResponse;
import common.UserManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream("config/server.properties")) {
            properties.load(in);
        } catch (Exception e) {
            System.err.println("Errore: Impossibile leggere il file di configurazione");
            System.exit(1);
        }
        int tcpPort = Integer.parseInt(properties.getProperty("tcp.port"));
        int udpPort = Integer.parseInt(properties.getProperty("udp.port"));
        int gameDurationSeconds = Integer.parseInt(properties.getProperty("game.duration.seconds"));
        int persistenceInterval = Integer.parseInt(properties.getProperty("persistence.interval.seconds"));
        String usersFile = properties.getProperty("users.file");
        UserManager userManager = new UserManager(usersFile); // creo oggetto di tipo UserManager
        userManager.load(); // carico in memoria il file utenti json

        PuzzleRepository puzzleRepo = new PuzzleRepository("data/Connections_Data.json");
        common.UdpNotifier udpNotifier = new common.UdpNotifier(udpPort);
        GameManager gameManager = new GameManager(puzzleRepo, gameDurationSeconds, userManager, udpNotifier);
        gameManager.loadHistory(); // carico cronologia partite da json

        int lastGameId = gameManager.getNextGameId();
        if (lastGameId >= 0) {
            // Impostiamo l'indice del puzzle repository in modo che salti le partite già
            // giocate
            puzzleRepo.setCurrentIndex(lastGameId + 1);
        }

        gameManager.startNewGame();
        Selector selector = Selector.open(); // apro selector e ServerSocketChannel configurandolo come non bloccante e
                                             // sulla porta 8080
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(tcpPort));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server NIO in ascolto sulla porta " + tcpPort + "...");
        ExecutorService pool = Executors.newFixedThreadPool(10); // fisso un thread pool a 10 thread per le varie
                                                                 // operazioni
        ScheduledExecutorService timedSaves = Executors.newSingleThreadScheduledExecutor(); // creo un salvataggio
                                                                                            // periodico ogni x secondi

        Runnable saves = new Runnable() {
            @Override
            public void run() {
                userManager.save();
                gameManager.saveHistory(); // persistenza periodica cronologia partite
            }
        };
        timedSaves.scheduleAtFixedRate(saves, 0, persistenceInterval, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            userManager.save();
            gameManager.saveHistory();
        })); // mi assicuro inoltre che alla chiusura del server venga salvato lo stato
             // finale

        while (true) { // ciclo infinito per le richieste
            selector.select();
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                iterator.remove();
                try {
                    if (key.isAcceptable()) { // se la chiave è ACCEPT accetto i vari client
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        ClientSession session = new ClientSession();// creo la sessione per client e la attacco alla
                                                                    // chiave
                        client.register(selector, SelectionKey.OP_READ, session);

                        System.out.println("Nuovo client connesso: " + client.getRemoteAddress());
                    } else if (key.isReadable()) { // se il client invia messaggi da leggere
                        SocketChannel client = (SocketChannel) key.channel();
                        ClientSession session = (ClientSession) key.attachment();
                        int bytesRead = client.read(session.readBuffer);
                        if (bytesRead == -1) {
                            System.out.println("Client disconnesso.");
                            userManager.logout(session);
                            userManager.removeSession(session);
                            key.cancel();
                            client.close();
                        } else {
                            List<String> messaggi = MessageReader.extractMessage(session.readBuffer); // estraggo il
                                                                                                      // messaggio e
                                                                                                      // lancio il task
                            for (String messaggio : messaggi) { // per ogni stringa presente in messaggi, capisco
                                                                // tramite il campo "operation" cosa vuole il client e
                                                                // chiamo la funzione di conseguenza
                                Runnable task = new Runnable() {
                                    @Override
                                    public void run() {
                                        JsonObject answer = new JsonObject();
                                        try {
                                            JsonObject request = JsonParser.parseString(messaggio).getAsJsonObject();
                                            String operation = request.get("operation").getAsString();
                                            switch (operation) {
                                                case "register":
                                                    String name = request.get("name").getAsString();
                                                    String psw = request.get("psw").getAsString();
                                                    boolean resRegistered = userManager.register(name, psw);

                                                    if (resRegistered) {
                                                        answer.addProperty("status", "ok");
                                                        answer.addProperty("message",
                                                                "Registrazione avvenuta con successo.");
                                                    } else {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente già esistente.");
                                                    }
                                                    break;
                                                case "updateCredentials":
                                                    String oldName = request.get("oldName").getAsString();
                                                    String oldPsw = request.get("oldPsw").getAsString();
                                                    String newName = null, newPsw = null;
                                                    if (request.has("newName")) {
                                                        newName = request.get("newName").getAsString();
                                                    }
                                                    if (request.has("newPsw")) {
                                                        newPsw = request.get("newPsw").getAsString();
                                                    }
                                                    ServerResponse resp = userManager.updateCredentials(oldName, oldPsw,
                                                            newName, newPsw);
                                                    answer.addProperty("status", resp.status);
                                                    answer.addProperty("message", resp.message);

                                                    if (resp.status.equals("ok") && newName != null
                                                            && session.LoggedUsername != null) {
                                                        if (session.LoggedUsername.equals(oldName)) {
                                                            session.LoggedUsername = newName;
                                                        }
                                                    }
                                                    break;
                                                case "login":
                                                    String loginName = request.get("username").getAsString();
                                                    String loginPsw = request.get("psw").getAsString();
                                                    int udpPort = request.get("udpPort").getAsInt();
                                                    ServerResponse resLogin = userManager.login(loginName, loginPsw);
                                                    answer.addProperty("status", resLogin.status);
                                                    answer.addProperty("message", resLogin.message);
                                                    if (resLogin.status.equals("ok")) {
                                                        session.LoggedUsername = loginName;
                                                        session.clientPort = udpPort;
                                                        gameManager.joinCurrentGame(loginName);
                                                        InetSocketAddress remoteAddress = (InetSocketAddress) client
                                                                .getRemoteAddress();
                                                        session.clInetAddress = remoteAddress.getAddress();
                                                        userManager.addSession(session);

                                                    }
                                                    break;
                                                case "logout":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        gameManager.leaveCurrentGame(session.LoggedUsername);
                                                        userManager.logout(session);
                                                        userManager.removeSession(session);
                                                        answer.addProperty("status", "ok");
                                                        answer.addProperty("message", "Logout effettuato.");
                                                    }

                                                    break;
                                                case "submitProposal":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        JsonArray wordsJson = request.getAsJsonArray("words");
                                                        List<String> words = new ArrayList<>();
                                                        if (wordsJson != null) {
                                                            for (JsonElement el : wordsJson) {
                                                                words.add(el.getAsString());
                                                            }
                                                        }
                                                        ServerResponse resSubmit = gameManager
                                                                .submitProposal(session.LoggedUsername, words);
                                                        answer.addProperty("status", resSubmit.status);
                                                        answer.addProperty("message", resSubmit.message);
                                                    }
                                                    break;
                                                case "requestGameInfo":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        int gameId = -1;
                                                        if (request.has("gameId")) {
                                                            gameId = request.get("gameId").getAsInt();
                                                        }
                                                        answer = gameManager.requestGameInfo(session.LoggedUsername,
                                                                gameId);
                                                    }
                                                    break;
                                                case "requestGameStats":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        int gameId = request.get("gameId").getAsInt();
                                                        JsonObject response = gameManager.requestGameStats(gameId);
                                                        answer = response;
                                                    }
                                                    break;
                                                case "requestLeaderboard":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        List<common.Leaderboard> leaderbord = userManager
                                                                .getLeaderboard();
                                                        if (request.has("playerName")) {
                                                            String playerName = request.get("playerName").getAsString();
                                                            boolean found = false;

                                                            for (int i = 0; i < leaderbord.size(); i++) {
                                                                if (leaderbord.get(i).username.equals(playerName)) {
                                                                    found = true;
                                                                    answer.addProperty("status", "ok");
                                                                    answer.addProperty("position", i + 1);
                                                                    answer.addProperty("score",
                                                                            leaderbord.get(i).score);
                                                                    break;
                                                                }
                                                            }
                                                            if (!found) {
                                                                answer.addProperty("status", "error");
                                                                answer.addProperty("message", "Giocatore non trovato");
                                                            }
                                                        } else if (request.has("topPlayers")) {
                                                            int limit = request.get("topPlayers").getAsInt();
                                                            answer.addProperty("status", "ok");
                                                            JsonArray jsonList = new JsonArray();
                                                            for (int i = 0; i < Math.min(limit,
                                                                    leaderbord.size()); i++) {
                                                                JsonObject lbElement = new JsonObject();
                                                                lbElement.addProperty("username",
                                                                        leaderbord.get(i).username);
                                                                lbElement.addProperty("score", leaderbord.get(i).score);
                                                                jsonList.add(lbElement);
                                                            }
                                                            answer.add("leaderboard", jsonList);
                                                        } else {
                                                            answer.addProperty("status", "ok");
                                                            JsonArray jsonList = new JsonArray();
                                                            for (common.Leaderboard lb : leaderbord) {
                                                                JsonObject lbElement = new JsonObject();
                                                                lbElement.addProperty("username", lb.username);
                                                                lbElement.addProperty("score", lb.score);
                                                                jsonList.add(lbElement);
                                                            }
                                                            answer.add("leaderboard", jsonList);
                                                        }
                                                    }
                                                    break;
                                                case "requestPlayerStats":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        JsonObject stats = userManager
                                                                .requestPlayerStats(session.LoggedUsername);
                                                        answer = stats;
                                                    }
                                                    break;
                                                default:
                                                    answer.addProperty("status", "error");
                                                    answer.addProperty("message", "Operazione non supportata");
                                                    break;
                                            }
                                        } catch (Exception e) {
                                            answer = new JsonObject();
                                            answer.addProperty("status", "error");
                                            answer.addProperty("message", "JSON non valido");
                                        }
                                        String risposta = answer.toString() + "\n";
                                        ByteBuffer outBuf = ByteBuffer
                                                .wrap(risposta.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                        try {
                                            while (outBuf.hasRemaining()) {
                                                client.write(outBuf);
                                            }
                                        } catch (Exception e) {
                                            System.err.println("Errrore invio risposta: " + e.getMessage());
                                        }
                                    }
                                };
                                pool.execute(task);
                            }
                        }
                    }
                } catch (IOException e) {
                    // Se un client crasha, cancelliamo la chiave e chiudiamo il canale
                    ClientSession session = (ClientSession) key.attachment();
                    if (session != null) {
                        gameManager.leaveCurrentGame(session.LoggedUsername);
                        userManager.logout(session);
                        userManager.removeSession(session);
                    }
                    key.cancel();
                    key.channel().close();
                }
            }

        }
    }
}
