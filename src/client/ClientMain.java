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
    public static volatile boolean isLoggedIn = false;
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
            InetSocketAddress serverAddress = new InetSocketAddress(serverIp, serverPort);
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(serverAddress);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (!socketChannel.finishConnect()) {
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
            CliThread cliTask = new CliThread(queue, selector, udpPort);
            Thread cliThread = new Thread(cliTask);
            cliThread.setDaemon(true);
            cliThread.start();
            while (true) {
               
                selector.select();
                String messageCli;
                while ((messageCli = queue.poll()) != null) {
                    try {
                        ByteBuffer writeBuffer = ByteBuffer.wrap(messageCli.getBytes("UTF-8"));
                        while (writeBuffer.hasRemaining()) {
                            socketChannel.write(writeBuffer);
                        }
                    } catch (Exception e) {
                        System.err.println("Errore durante l'invio del messaggio: " + e.getMessage());
                    }
                }
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

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
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleServerMessage(String json) {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            String status = "unknown", message = "";
            if (response.has("status")) {
                status = response.get("status").getAsString();
            }
            if (response.has("message")) {
                message = response.get("message").getAsString();
            }

            // messaggi generali
            if (status.equals("ok") || status.equals("ongoing") || status.equals("finished")) {
                if (!message.isEmpty()) {
                    System.out.println("\n[✅] -> " + message);
                }
            } else if (status.equals("error")) {
                System.out.println("\n[❌] -> " + message);
            }

            // info partita
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
            // parole
            if (response.has("words")) {
                System.out.println("- Parole da raggruppare: " + response.get("words").getAsJsonArray().toString());
            } else if (response.has("remainingWords")) {
                System.out.println(
                        "- Parole da raggruppare: " + response.get("remainingWords").getAsJsonArray().toString());
            }
            if (response.has("correctGroups")) {
                System.out.println("- Gruppi indovinati: " + response.get("correctGroups").getAsJsonArray().toString());
            }

            // leaderboard
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

            // Statistiche partita
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

            // gestione login/logout
            if (status.equals("ok")) {
                if (json.toLowerCase().contains("loggato")) {
                    isLoggedIn = true;
                } else if (json.toLowerCase().contains("logout")) {
                    isLoggedIn = false;
                }
            }
        } catch (Exception e) {
            System.out.println("\n[MESSAGGIO DI RETE] -> " + json);
        } finally {
            waitingForResponse = false;
        }
    }
}
