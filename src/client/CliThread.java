package client;

import java.nio.channels.Selector;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class CliThread implements Runnable {
    private final ConcurrentLinkedQueue<String> queue;
    private final Selector selector;
    private final int udpPort;

    public CliThread(ConcurrentLinkedQueue<String> queue, Selector selector, int udpPort) { //costruttore
        this.selector = selector;
        this.udpPort = udpPort;
        this.queue = queue;
    }

    @Override
    public void run() {
        Scanner in = new Scanner(System.in); 
        while (true) {
            if (!ClientMain.isLoggedIn) { //se non è loggato mostro il menu di login
                System.out.println("--- Benvenuto in CONNECTION ---");
                System.out.println("1. Registrati\n2. Login\n0. Esci");
                String command = in.nextLine().trim();

                switch (command) {
                    case "0":
                        System.out.println("Uscita in corso...");
                        System.exit(0);
                        break;
                    case "1":
                        System.out.println("Inserisci il nome.");
                        String name = in.nextLine().trim();
                        System.out.println("Inserisci una password.");
                        String psw = in.nextLine().trim();
                        JsonObject reqRegister = new JsonObject();
                        reqRegister.addProperty("operation", "register");
                        reqRegister.addProperty("name", name);
                        reqRegister.addProperty("psw", psw);
                        ClientMain.waitingForResponse = true;
                        queue.add(reqRegister.toString() + "\n");
                        selector.wakeup();
                        System.out.println("Richiesta inviata. Attendo risposta dal server...");
                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        break;
                    case "2":
                        System.out.println("Username: ");
                        String username = in.nextLine().trim();
                        System.out.println("Password: ");
                        String pswLogin = in.nextLine().trim();
                        JsonObject reqLogin = new JsonObject();
                        reqLogin.addProperty("operation", "login");
                        reqLogin.addProperty("username", username);
                        reqLogin.addProperty("psw", pswLogin);
                        reqLogin.addProperty("udpPort", udpPort);
                        ClientMain.waitingForResponse = true; // questa variabile serve per evitare Race Condition,
                                                              // impostandolo a true il thread rimane bloccato
                                                              // mettendolo in sleep ongi 100 millisecondi quando la
                                                              // risposta cambia esce dal ciclo e si può andare avanti.
                        queue.add(reqLogin.toString() + "\n");
                        selector.wakeup();
                        System.out.println("Richiesta inviata. Attendo risposta dal server...");
                        while (ClientMain.waitingForResponse) {// il ciclo che blocca
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        if (ClientMain.isLoggedIn) {
                            JsonObject gameWords = new JsonObject();
                            gameWords.addProperty("operation", "requestGameInfo");
                            gameWords.addProperty("gameId", -1);
                            ClientMain.waitingForResponse = true;
                            queue.add(gameWords.toString() + "\n");
                            selector.wakeup();

                            while (ClientMain.waitingForResponse) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                        break;
                    default:
                        System.out.println("Comando non valido, inserisci un comando valido!");
                        break;
                }
            } else { // se è loggato mostro il menu di gioco
                System.out.println("--- Menu di Gioco ---");
                System.out.println(
                        "1. Invia Proposta\n2. Info Partita\n3. Statistiche Partita\n4. Statistiche Giocatore\n5. Classifica\n6. Aggiorna Credenziali\n0. Logout");
                String command = in.nextLine().trim();
                switch (command) {
                    case "0":
                        System.out.println("Logout in corso...");
                        JsonObject reqLogout = new JsonObject();
                        reqLogout.addProperty("operation", "logout");
                        ClientMain.waitingForResponse = true;
                        queue.add(reqLogout.toString() + "\n");
                        selector.wakeup();
                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        break;
                    case "1":
                        JsonObject gameWords = new JsonObject();
                        gameWords.addProperty("operation", "requestGameInfo");
                        gameWords.addProperty("gameId", -1);
                        ClientMain.waitingForResponse = true;
                        queue.add(gameWords.toString() + "\n");
                        selector.wakeup();

                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        JsonObject submitProposal = new JsonObject();
                        submitProposal.addProperty("operation", "submitProposal");
                        System.out.println("Inserisci le 4 parole che secondo te appartengono allo stesso gruppo:");
                        JsonArray words = new JsonArray();
                        for (int i = 0; i < 4; i++) {
                            System.out.println("Scrivi la " + (i + 1) + "° parola:");
                            String parola = in.nextLine().trim().toUpperCase();
                            words.add(parola);
                        }
                        submitProposal.add("words", words);
                        ClientMain.waitingForResponse = true;
                        queue.add(submitProposal.toString() + "\n");
                        selector.wakeup();

                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        break;
                    case "2":
                        System.out.println(
                                "Che info partita vuoi? (Digita -1 se vuoi quelle della partita corrente, oppure digita il game id di una partita vecchia)");
                        String gamePreference = in.nextLine().trim();
                        int gameId = -1;
                        try {
                            gameId = Integer.parseInt(gamePreference);
                            JsonObject reqGameInfo = new JsonObject();
                            reqGameInfo.addProperty("operation", "requestGameInfo");
                            reqGameInfo.addProperty("gameId", gameId);
                            ClientMain.waitingForResponse = true;
                            queue.add(reqGameInfo.toString() + "\n");
                            selector.wakeup();
                            while (ClientMain.waitingForResponse) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Errore devi inserire un numero valido!");
                        }

                        break;
                    case "3":
                        System.out.println(
                                "Che statistiche partita vuoi? (Digita -1 se vuoi quelle della partita corrente, oppure digita il game id di una partita vecchia)");
                        String statsPreference = in.nextLine().trim();
                        int statsId = -1;
                        try {
                            statsId = Integer.parseInt(statsPreference);
                            JsonObject reqGameStats = new JsonObject();
                            reqGameStats.addProperty("operation", "requestGameStats");
                            reqGameStats.addProperty("gameId", statsId);
                            ClientMain.waitingForResponse = true;
                            queue.add(reqGameStats.toString() + "\n");
                            selector.wakeup();
                            while (ClientMain.waitingForResponse) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("Errore devi inserire un numero valido!");

                        }
                        break;
                    case "4":
                        JsonObject reqPlayerStats = new JsonObject();
                        reqPlayerStats.addProperty("operation", "requestPlayerStats");
                        ClientMain.waitingForResponse = true;
                        queue.add(reqPlayerStats.toString() + "\n");
                        selector.wakeup();
                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        break;
                    case "5":
                        JsonObject reqLeaderboard = new JsonObject();
                        reqLeaderboard.addProperty("operation", "requestLeaderboard");
                        System.out.println("Scegli un'opzione per la classifica:");
                        System.out.println(
                                "1. Tutta la classifica completa\n2. Top K giocatori (es. i primi 5)\n3. Cerca la posizione di un giocatore specifico");
                        String choise = in.nextLine().trim();

                        boolean manda = true;
                        if (choise.equals("2")) {
                            System.out.println("Inserisci il numero di giocatori.");
                            try {
                                int topK = Integer.parseInt(in.nextLine().trim());
                                reqLeaderboard.addProperty("topPlayers", topK);
                            } catch (Exception e) {
                                System.out.println("❌: devi inserire un numero!");
                                manda = false;
                            }
                        } else if (choise.equals("3")) {
                            System.out.println("Inserisci il nome del giocatore: ");
                            String name = in.nextLine().trim();
                            reqLeaderboard.addProperty("playerName", name);
                        }
                        if (manda) {
                            ClientMain.waitingForResponse = true;
                            queue.add(reqLeaderboard.toString() + "\n");
                            selector.wakeup();
                            while (ClientMain.waitingForResponse) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                        break;
                    case "6":
                        System.out.println("Inserisci l'username attuale:");
                        String oldName = in.nextLine().trim();
                        System.out.println("Inserisci la password attuale:");
                        String oldPsw = in.nextLine().trim();

                        System.out.println("Inserisci il nuovo username (lascia vuoto per non cambiarlo):");
                        String newName = in.nextLine().trim();
                        System.out.println("Inserisci la nuova password (lascia vuoto per non cambiarla):");
                        String newPsw = in.nextLine().trim();
                        
                        JsonObject updateReq = new JsonObject();
                        updateReq.addProperty("operation", "updateCredentials");
                        updateReq.addProperty("oldName", oldName);
                        updateReq.addProperty("oldPsw", oldPsw);

                        if (!newName.isEmpty()) {
                            updateReq.addProperty("newName", newName);
                        }
                        if (!newPsw.isEmpty()) {
                            updateReq.addProperty("newPsw", newPsw);
                        }

                        String jsonStringUpdate = updateReq.toString() + "\n";

                        ClientMain.waitingForResponse = true;
                        queue.add(jsonStringUpdate);
                        selector.wakeup();

                        while (ClientMain.waitingForResponse) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                        }
                        break;
                    default:
                        System.out.println("Comando non valido, inserisci un comando valido!");
                        break;
                }
            }
        }
    }
}
