package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UdpListener implements Runnable { //thread per ascoltare le notifiche inviate dal server
    Gson gson = new Gson();
    DatagramSocket dS = null;
    int port = -1;

    public int initSocket() throws SocketException {
        dS = new DatagramSocket(0);
        dS.setSoTimeout(1000);
        this.port = dS.getLocalPort();
        return port;
    }

    @Override
    public void run() {
        if (dS == null) {
            System.err.println("Errore: datagramSocket non inizializzato.");
            return;
        }
        System.out.println("[UDP] In ascolto per notifiche sulla porta: " + port);
        while (true) {
            byte[] buffer = new byte[8192];
            DatagramPacket dP = new DatagramPacket(buffer, buffer.length);
            try {
                dS.receive(dP); 
                String recivedString = new String(dP.getData(), 0, dP.getLength(), "UTF-8"); 
                try {
                    JsonObject gameEnd = JsonParser.parseString(recivedString).getAsJsonObject();
                    System.out.println("\n===== [NOTIFICA DAL SERVER: PARTITA TERMINATA 🔔] =====");
                    System.out.println(
                            "- Giocatori totali che hanno partecipato: " + gameEnd.get("totalPlayers").getAsInt());
                    System.out.println(
                            "- Giocatori che hanno concluso in tempo: " + gameEnd.get("finishedPlayers").getAsInt());
                    System.out.println("- Vincitori totali: " + gameEnd.get("winnersCount").getAsInt());
                    System.out.println("- Punteggio medio: " + gameEnd.get("agvScore").getAsDouble());
                    System.out.println("- LA SOLUZIONE COMPLETA ERA:");

                    JsonArray solutionArray = gameEnd.getAsJsonArray("solution");
                    for (int i = 0; i < solutionArray.size(); i++) {
                        JsonObject group = solutionArray.get(i).getAsJsonObject();
                        System.out.println("  > TEMA: " + group.get("theme").getAsString() + " -> "
                                + group.get("words").getAsJsonArray().toString());
                    }
                    System.out.println("=======================================================\n");

                } catch (Exception e) {
                    System.out.println("\n[NOTIFICA DAL SERVER]: " + recivedString);
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch (Exception e) {
                System.err.println("Errore nel listener UDP: " + e.getMessage());
                break;
            }
        }
        dS.close();
    }
}
