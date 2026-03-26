package server;

import java.nio.ByteBuffer;
import java.net.InetAddress;

public class ClientSession {//questa classe mi permette di sapere lo stato della sessione di ongi client
    public ByteBuffer readBuffer = ByteBuffer.allocate(4096);
    public String LoggedUsername = null;
    public int clientPort = -1;
    public InetAddress clInetAddress = null;
}
