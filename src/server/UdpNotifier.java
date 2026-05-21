package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;

import common.GameEndNotification;

public class UdpNotifier {

    private DatagramSocket dS;
    private final Gson gson = new Gson();

    public UdpNotifier(int port) throws SocketException { // definisco datagramsocket e lego la porta
        this.dS = new DatagramSocket(port);
    }

    public void notifyAllPlayer(List<ClientSession> sessions, GameEndNotification message) throws IOException {// metodo per inviare notifiche a tutti i client connessi
                                                                                                               
        if (dS == null) {
            return;
        }
        String json = gson.toJson(message);// converto il messaggio in json 
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < sessions.size(); i++) { //recupero le informazioni per ogni client
            ClientSession session = sessions.get(i);
            int clientPort = session.clientPort;
            InetAddress clInetAddress = session.clInetAddress;
            if (clInetAddress == null || clientPort <= 0) {
                continue;
            }
            DatagramPacket dP = new DatagramPacket(bytes, bytes.length, clInetAddress, clientPort); //creo il pacchetto e invio
            dS.send(dP);
        }
    }
}
