package server;

import java.nio.ByteBuffer;
import java.net.InetAddress;

public class ClientSession {// questa classe mi permette di sapere lo stato della sessione di ongi client
    public ByteBuffer readBuffer = ByteBuffer.allocate(4096); // buffer personale
    public String LoggedUsername = null; // username unico del client
    public int clientPort = -1; // porta per notifiche udp
    public InetAddress clInetAddress = null; // indirizzo ip
}
