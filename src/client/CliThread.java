package client;

import java.nio.channels.Selector;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CliThread implements Runnable {
    private final ConcurrentLinkedQueue<String> queue;
    private final Selector selector;
    private final int udpPort;

    public CliThread(ConcurrentLinkedQueue<String> queue, Selector selector, int udpPort) {
        this.selector = selector;
        this.udpPort = udpPort;
        this.queue = queue;
    }

    @Override
    public void run() {
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("Benvenuto :)\n1. Registrati\n2. Login\n3. Logout\n0. Esci");
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
                    String reqRegister = String
                            .format("{\"operation\": \"register\", \"name\": \"%s\", \"psw\": \"%s\"}\n", name, psw);
                    queue.add(reqRegister);
                    selector.wakeup();
                    System.out.println("Richiesta inviata. Attendo risposta dal server...");
                    break;
                case "2":
                    System.out.println("Username: ");
                    String username = in.nextLine().trim();
                    System.out.println("Password: ");
                    String pswLogin = in.nextLine().trim();
                    String reqLogin = String.format(
                            "{\"operation\": \"login\", \"username\": \"%s\", \"psw\": \"%s\", \"udpPort\": %d}\n",
                            username, pswLogin, udpPort);
                    queue.add(reqLogin);
                    selector.wakeup();
                    System.out.println("Richiesta inviata. Attendo risposta dal server...");

                    break;
                case "3":
                    System.out.println("Logout in corso...");
                    String reqLogout = "{\"operation\": \"logout\"}\n";
                    queue.add(reqLogout);
                    selector.wakeup();
                    break;
                default:
                    System.out.println("Comando non valido, inserisci un comando valido!");
                    break;
            }
        }
    }
}
