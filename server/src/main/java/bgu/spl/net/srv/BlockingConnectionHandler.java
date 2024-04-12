package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.BidiMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private int connection_id;
    private Connections<T> connections_list;
    private final Object sendLock = new Object(); // A lock object for synchronization


    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol,int connection_id, Connections<T> connections_list) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connection_id = connection_id;
        this.connections_list = connections_list;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            protocol.start(connection_id, connections_list);// Idan and Noa wrote this!

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                // System.out.println("Next message is: "+ nextMessage);
                if (nextMessage != null) {
                    protocol.process(nextMessage,out);

                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }


    @Override
    public void send(T msg) {
        synchronized(sendLock) { // Synchronize on the sendLock object
            try {
                if (msg != null) {
                    out.write(encdec.encode(msg));
                    out.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
