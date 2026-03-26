package common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageReader { //parser per i messaggi 
    public static List<String> extractMessage(ByteBuffer buf) throws IOException {
        buf.flip();
        List<String> lista = new ArrayList<>();
        int start = 0;
        while (buf.hasRemaining()) {
            if (buf.get() == '\n') {
                int finalPosition = buf.position() - 1;
                int tempPosition = buf.position();
                buf.position(start);
                byte[] currentLength = new byte[finalPosition - start];
                buf.get(currentLength);
                lista.add(new String(currentLength, java.nio.charset.StandardCharsets.UTF_8));
                buf.position(tempPosition);
                start = tempPosition;
            }
        }
        buf.position(start);
        buf.compact();
        return lista;
    }
}
