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

            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            UdpListener udpListener = new UdpListener();
            Thread t = new Thread(udpListener);
            t.setDaemon(true); // setto il thread come Deamon in modo tale che quando chiudo il client venga
                               // chiuso anche questo thread
            t.start();
            int udpPort = Integer.parseInt(config.getProperty("udp.port"));
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
        System.out.println("\n[RISPOSTA DAL SERVER] -> " + json);
    }
}
