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

import common.MessageReader;

public class ClientMain {
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
            // --- INIZIO TEST HARDCODATO (PUNTO 4) ---
            // Invio manuale di una richiesta di LOGIN (sostituisci con le tue credenziali
            // se diverse)
            String testRequest = "{\"operation\": \"login\", \"username\": \"mario\", \"psw\": \"rossi123\", \"udpPort\": 9000}\n";
            ByteBuffer writeBuffer = ByteBuffer.wrap(testRequest.getBytes("UTF-8"));
            socketChannel.write(writeBuffer);
            System.out.println("Richiesta di test (login) inviata!");
            // --- FINE TEST HARDCODATO ---

            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            while (true) {
                selector.select();
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
        System.out.println("\n[RISPOSTA DAL SERVER] -> " + json);
    }
}
