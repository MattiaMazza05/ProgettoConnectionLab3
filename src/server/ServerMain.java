package server;

import common.MessageReader;
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
                        int bytesRead = client.read(session.buffer);

                        if (bytesRead == -1) {
                            // Il client ha chiuso la connessione
                            System.out.println("Client disconnesso.");
                            key.cancel();
                            client.close();
                        } else {
                            List<String> messaggi = MessageReader.extractMessage(session.buffer);
                            for (String messaggio : messaggi) {
                                Runnable task = new Runnable() {
                                    @Override
                                    public void run() {
                                        System.out.println("Ricevuto JSON completo: " + messaggio);
                                        JsonObject request = JsonParser.parseString(messaggio).getAsJsonObject();
                                        String operation = request.get("operation").getAsString();
                                        ByteBuffer outBuf;
                                        if (operation.equals("register")) {
                                            String username = request.get("username").getAsString();
                                            String password = request.get("password").getAsString();
                                            boolean statusRegistered = userManager.register(username, password);
                                            JsonObject answer = new JsonObject();
                                            if (statusRegistered) {
                                                answer.addProperty("status", "ok");
                                                answer.addProperty("message", "Registrazione avvenuta con successo.");
                                            } else {
                                                answer.addProperty("status", "error");
                                                answer.addProperty("message", "Utente già esistente.");
                                            }
                                            String risposta = answer.toString() + "\n";
                                            byte[] bytes = risposta.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                            outBuf = ByteBuffer.wrap(bytes);
                                            while (outBuf.hasRemaining()) {
                                                try {
                                                    client.write(outBuf);
                                                } catch (Exception e) {
                                                    System.out.println(e);
                                                }
                                            }
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

    static class ClientSession {
        public ByteBuffer buffer = ByteBuffer.allocate(1024);
    }
}
