package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class ClientTest {
    public static void main(String[] args) throws java.io.IOException {
        DatagramSocket dtS = new DatagramSocket(8081);
        byte[] bytes = new byte[1024];
        DatagramPacket dtP = new DatagramPacket(bytes, bytes.length);
        dtS.receive(dtP);
        String stringaRicevuta = new String(dtP.getData(), 0, dtP.getLength(), StandardCharsets.UTF_8);
        System.out.println(stringaRicevuta);
        dtS.close();
    }
}
