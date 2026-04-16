package client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Properties;

import com.google.gson.Gson;

public class UdpListener implements Runnable {
    Gson gson = new Gson();

    @Override
    public void run() {
        int udpPort = -1;
        Properties config = new Properties();
        try (InputStream in = new FileInputStream("config/client.properties")) {
            config.load(in);
            udpPort = Integer.parseInt(config.getProperty("udp.port"));
        } catch (Exception e) {
            System.err.println("Errore di caricamento client.properties nel UdpListener: " + e.getMessage());
            return;
        }
        try (DatagramSocket dS = new DatagramSocket(udpPort)) {
            dS.setSoTimeout(1000);
            System.out.println("[UDP] In ascolto per notifiche sulla porta: " + udpPort);
            while (true) {
                byte[] buffer = new byte[8192];
                DatagramPacket dP = new DatagramPacket(buffer, buffer.length);
                try {
                    dS.receive(dP);
                    String recivedString = new String(dP.getData(), 0, dP.getLength(), "UTF-8");
                    System.out.println("\n[NOTIFICA DAL SERVER]: " + recivedString);

                } catch (SocketTimeoutException e) {
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("Errore nel listener UDP: " + e.getMessage());
        }

    }
}
