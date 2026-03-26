package server;

import common.MessageReader;
import common.ServerResponse;
import common.UserManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        UserManager userManager = new UserManager();
        userManager.load();
        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(8080));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server NIO in ascolto sulla porta 8080...");

        ExecutorService pool = Executors.newFixedThreadPool(10);
        ScheduledExecutorService timedSaves = Executors.newSingleThreadScheduledExecutor();

        Runnable saves = new Runnable() {
            @Override
            public void run() {
                userManager.save();
            }
        };
        timedSaves.scheduleAtFixedRate(saves, 0, 10, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> userManager.save()));
        while (true) {
            selector.select();
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                iterator.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();

                        // Anche il client deve essere non bloccante!
                        client.configureBlocking(false);

                        // Creiamo la sessione per questo client e la attacchiamo alla chiave
                        ClientSession session = new ClientSession();
                        client.register(selector, SelectionKey.OP_READ, session);

                        System.out.println("Nuovo client connesso: " + client.getRemoteAddress());
                    }
                    // CASO B: Un client ha inviato dei dati da leggere
                    else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ClientSession session = (ClientSession) key.attachment();

                        // Leggiamo i dati dal canale e li mettiamo nel buffer del client
                        int bytesRead = client.read(session.readBuffer);

                        if (bytesRead == -1) {
                            // Il client ha chiuso la connessione
                            System.out.println("Client disconnesso.");
                            userManager.logout(session);
                            key.cancel();
                            client.close();
                        } else {
                            List<String> messaggi = MessageReader.extractMessage(session.readBuffer);
                            for (String messaggio : messaggi) {
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
                                                    ServerResponse resLogin = userManager.login(loginName, loginPsw);
                                                    answer.addProperty("status", resLogin.status);
                                                    answer.addProperty("message", resLogin.message);
                                                    if (resLogin.status.equals("ok")) {
                                                        session.LoggedUsername = loginName;
                                                    }
                                                    break;
                                                case "logout":
                                                    if (session.LoggedUsername == null) {
                                                        answer.addProperty("status", "error");
                                                        answer.addProperty("message", "Utente non loggato.");
                                                    } else {
                                                        userManager.logout(session);
                                                        answer.addProperty("status", "ok");
                                                        answer.addProperty("message", "Logout effettuato.");
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
