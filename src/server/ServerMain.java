package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(8080));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server NIO in ascolto sulla porta 8080...");

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
                            // REGOLA D'ORO DEL BUFFER: flip() prima di leggere i dati!
                            session.buffer.flip();

                            // Estraiamo i byte e li stampiamo come Stringa
                            byte[] data = new byte[session.buffer.limit()];
                            session.buffer.get(data);
                            String messaggio = new String(data, "UTF-8").trim();

                            System.out.println("Ricevuto: " + messaggio);

                            // Svuotiamo il buffer per la prossima lettura
                            session.buffer.clear();
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
