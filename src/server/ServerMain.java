package server;

import common.GameManager;
import common.MessageReader;
import common.PuzzleRepository;
import common.ServerResponse;
import common.UserManager;
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
        UserManager userManager = new UserManager(); // creo oggetto di tipo UserManager
        userManager.load(); // carico in memoria il file utenti json
        int gameDurationSeconds = 30;
        userManager.load();
        PuzzleRepository puzzleRepo = new PuzzleRepository("data/Connections_Data.json");
        common.UdpNotifier udpNotifier = new common.UdpNotifier();
        GameManager gameManager = new GameManager(puzzleRepo, gameDurationSeconds, userManager, udpNotifier);
        gameManager.startNewGame();
        Selector selector = Selector.open(); // apro selector e ServerSocketChannel configurandolo come non bloccante e
                                             // sulla porta 8080
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(8080));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server NIO in ascolto sulla porta 8080...");
        ExecutorService pool = Executors.newFixedThreadPool(10); // fisso un thread pool a 10 thread per le varie
                                                                 // operazioni
        ScheduledExecutorService timedSaves = Executors.newSingleThreadScheduledExecutor(); // creo un salvataggio
                                                                                            // periodico ogni 10 secondi

        Runnable saves = new Runnable() {
            @Override
            public void run() {
                userManager.save();
            }
        };
        timedSaves.scheduleAtFixedRate(saves, 0, 10, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> userManager.save())); // mi assicuro inoltre che alla
                                                                                    // chiusura del server venga salvato
                                                                                    // lo stato attuale

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
                                                        InetSocketAddress remoteAddress = (InetSocketAddress) client
                                                                .getRemoteAddress();
                                                        session.clInetAddress = remoteAddress.getAddress();
                                                        gameManager.joinCurrentGame(loginName);
                                                        userManager.addSession(session);
                                                    }
                                                    break;
                                                case "logout":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
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
                    key.cancel();
                    key.channel().close();
                }
            }

        }
    }
}
