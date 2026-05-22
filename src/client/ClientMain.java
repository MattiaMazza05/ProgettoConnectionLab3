package client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.MessageReader;

public class ClientMain {
    public static volatile boolean isLoggedIn = false;  //isLoggedIn tiene traccia dello stato dell'utent per ecitare che mandi comandi se non è loggato, waitingForResponse serve per evitare che mandi piu comandi contemporaneamene 
    public static volatile boolean waitingForResponse = false;
    public static void main(String[] args) {
        try {
            Properties config = new Properties();
            try (InputStream in = new FileInputStream("config/client.properties")) {
                config.load(in);
            }
            String serverIp = config.getProperty("server.ip");
            int serverPort = Integer.parseInt(config.getProperty("server.tcp.port"));
            Selector selector = Selector.open();
            InetSocketAddress serverAddress = new InetSocketAddress(serverIp, serverPort); // mi connetto al server
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(serverAddress);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (!socketChannel.finishConnect()) {// mi assicuro che si sia connesso 
                selector.select(100);
            }
            socketChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("Connesso con successo al server " + serverIp + ":" + serverPort);

            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            int udpPort = -1;
            UdpListener udpListener = new UdpListener(); 
            udpPort = udpListener.initSocket();
            Thread t = new Thread(udpListener);
            t.setDaemon(true); // setto il thread come Deamon in modo tale che quando chiudo il client venga
                               // chiuso anche questo thread
            t.start();
            CliThread cliTask = new CliThread(queue, selector, udpPort); // creo un thread per gestire l'input 
            Thread cliThread = new Thread(cliTask);
            cliThread.setDaemon(true);
            cliThread.start();
            ByteBuffer currentWriteBuffer = null; // Buffer per gestire eventuali scritture parziali

            while (true) {
                SelectionKey channelKey = socketChannel.keyFor(selector);
                if (channelKey != null && channelKey.isValid()) {
                    if (!queue.isEmpty() || (currentWriteBuffer != null && currentWriteBuffer.hasRemaining())) { //controllo il buffer e la coda, se c'è qualcosa da scrivere imposto in OP_WRITE
                        channelKey.interestOps(channelKey.interestOps() | SelectionKey.OP_WRITE);
                    } else {
                        channelKey.interestOps(channelKey.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                }

                selector.select();
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()){
                        continue;
                    }

                    if (key.isReadable()) { 
                        SocketChannel channel = (SocketChannel) key.channel();
                        int bytesRead = channel.read(readBuffer);

                        if (bytesRead == -1) {
                            System.out.println("Connessione chiusa dal server.");
                            key.cancel();
                            channel.close();
                            return;
                        }
                        List<String> messages = MessageReader.extractMessage(readBuffer);
                        for (String msg : messages) {
                            handleServerMessage(msg);
                        }
                    }

                    if (key.isWritable()) { 
                        SocketChannel channel = (SocketChannel) key.channel();

                        if (currentWriteBuffer == null || !currentWriteBuffer.hasRemaining()) { //prendo il prox messaggio dalla coda se è vuota
                            String messageCli = queue.poll();
                            if (messageCli != null) {
                                currentWriteBuffer = ByteBuffer.wrap(messageCli.getBytes("UTF-8"));
                            }
                        }

                        if (currentWriteBuffer != null && currentWriteBuffer.hasRemaining()) {
                            try {
                                channel.write(currentWriteBuffer);
                            } catch (Exception e) {
                                System.err.println("Errore durante l'invio del messaggio: " + e.getMessage());
                                key.cancel();
                                channel.close();
                                return;
                            }
                        }
                        //se il buffer è vuoto levo OP_WRITE
                        if (currentWriteBuffer == null || !currentWriteBuffer.hasRemaining()) {
                            if (queue.isEmpty()) {
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleServerMessage(String json) { // questo metodo interpreta i messaggi ricevuti dal server e stampa con una migliore formattazione
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            String status = "unknown", message = "";
            if (response.has("status")) {
                status = response.get("status").getAsString();
            }
            if (response.has("message")) {
                message = response.get("message").getAsString();
            }

            // --- MESSAGGI GENERALI ---\
            if (status.equals("ok") || status.equals("ongoing") || status.equals("finished")) {
                if (!message.isEmpty()) {
                    System.out.println("\n[✅] -> " + message);
                }
            } else if (status.equals("error")) {
                System.out.println("\n[❌] -> " + message);
            }

            // --- DETTAGLI PARTITA ---
            if (response.has("gameId")) {
                System.out.println("\n--- Info Partita ID: " + response.get("gameId").getAsInt() + " ---");
            }
            if (response.has("status") && (response.get("status").getAsString().equals("ongoing")
                    || response.get("status").getAsString().equals("finished"))) {
                System.out.println("- Stato partita: " + response.get("status").getAsString());
            }
            if (response.has("solution")) {
                if (response.get("solution").isJsonPrimitive()) {
                    System.out.println("- Soluzione : " + response.get("solution").getAsString());
                } else {
                    System.out.println("- Soluzione: " + response.get("solution").getAsJsonArray().toString());
                }
            }
            if (response.has("timeRemaining")) {
                System.out.println("- Tempo rimasto ⏱️: " + response.get("timeRemaining").getAsInt() + " sec");
            }
            if (response.has("score")) {
                System.out.println("- Punteggio 💯: " + response.get("score").getAsInt());
            }
            if (response.has("mistakes")) {
                System.out.println("- Errori 🙈: " + response.get("mistakes").getAsInt());
            }
            // --- DETTAGLI PUZZLE ---
            if (response.has("words")) {
                System.out.println("- Parole da raggruppare: " + response.get("words").getAsJsonArray().toString());
            } else if (response.has("remainingWords")) {
                System.out.println(
                        "- Parole da raggruppare: " + response.get("remainingWords").getAsJsonArray().toString());
            }
            if (response.has("correctGroups")) {
                System.out.println("- Gruppi indovinati: " + response.get("correctGroups").getAsJsonArray().toString());
            }
            if (response.has("groupFound")) {
                JsonObject group = response.getAsJsonObject("groupFound");
                System.out.println("- Gruppo trovato: " + group.get("theme").getAsString() + " -> "
                        + group.get("words").getAsJsonArray().toString());
            }
            // --- LEADERBOARD ---
            if (response.has("position")) {
                System.out.println("- Sei in posizione 🥇: " + response.get("position").getAsInt());
            }
            if (response.has("leaderboard")) {
                System.out.println("\n--- CLASSIFICA ---");
                JsonArray board = response.getAsJsonArray("leaderboard");
                for (int i = 0; i < board.size(); i++) {
                    JsonObject el = board.get(i).getAsJsonObject();
                    System.out.println((i + 1) + ". " + el.get("username").getAsString() + " | Punti: "
                            + el.get("score").getAsInt());
                }
            }

            // --- STATISTICHE PARTITA ---
            if (response.has("totalPlayers")) {
                System.out.println("- Giocatori totali in partita ⌛️: " + response.get("totalPlayers").getAsInt());
            }

            if (response.has("finishedPlayers")) {
                System.out.println("- Giocatori che hanno finito ✅: " + response.get("finishedPlayers").getAsInt());
            }
            if (response.has("winnersCount")) {
                System.out.println("- Giocatori che hanno vinto 🏆: " + response.get("winnersCount").getAsInt());
            }

            if (response.has("avgScore")) {
                System.out.println("- Punteggio medio : " + response.get("avgScore").getAsDouble());
            }
            // statistiche giocatore
            if (response.has("gamesPlayed")) {
                System.out.println("- Partite Giocate: " + response.get("gamesPlayed").getAsInt());
            } else if (response.has("puzzleCompleted")) {
                System.out.println("- Partite Giocate: " + response.get("puzzleCompleted").getAsInt());
            }

            if (response.has("perfectPuzzles")) {
                System.out.println("- Perfect Puzzles (0 errori): " + response.get("perfectPuzzles").getAsInt());
            } else if (response.has("perfectPuzzle")) {
                System.out.println("- Perfect Puzzles (0 errori): " + response.get("perfectPuzzle").getAsInt());
            }

            if (response.has("winRate")) {
                System.out.println("- Win Rate: " + response.get("winRate").getAsDouble() + "%");
            }
            if (response.has("lossRate")) {
                System.out.println("- Loss Rate: " + response.get("lossRate").getAsDouble() + "%");
            }
            if (response.has("currentStreak")) {
                System.out.println("- Streak (vittorie consecutive): " + response.get("currentStreak").getAsInt());
            }
            if (response.has("maxStreak")) {
                System.out.println("- Max Streak: " + response.get("maxStreak").getAsInt());
            }
            if (response.has("mistakeHistogram")) {
                System.out.println("- Istogramma Errori (da 0 a 4): "
                        + response.get("mistakeHistogram").getAsJsonArray().toString());
            }

            // --- GESTIONE LOGIN/LOGOUT ---
            if (status.equals("ok")) {
                if (json.toLowerCase().contains("loggato")) {
                    isLoggedIn = true;
                } else if (json.toLowerCase().contains("logout")) {
                    isLoggedIn = false;
                }
            }
        } catch (Exception e) {
            System.out.println("\n[MESSAGGIO DI RETE] -> " + json);
        } finally {// alla fine di ogni messaggio imposto waitingForResponse a falso così che il client può inviare nuovi comandi
            waitingForResponse = false;
        }
    }
}
