package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;

import server.ClientSession;

public class UdpNotifier {

    private DatagramSocket dS;
    private final Gson gson = new Gson();

    public UdpNotifier() throws SocketException {
        this.dS = new DatagramSocket();
    }

    public void notifyAllPlayer(List<ClientSession> sessions, GameEndNotification message) throws IOException {
        if (dS == null) {
            return;
        }
        String json = gson.toJson(message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < sessions.size(); i++) {
            ClientSession session = sessions.get(i);
            int clientPort = session.clientPort;
            InetAddress clInetAddress = session.clInetAddress;
            if (clInetAddress == null || clientPort <= 0) {
                continue;
            }
            DatagramPacket dP = new DatagramPacket(bytes, bytes.length, clInetAddress, clientPort);
            dS.send(dP);
        }
    }
}
