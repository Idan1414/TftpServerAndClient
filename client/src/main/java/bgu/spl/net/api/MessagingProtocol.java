package bgu.spl.net.api;

import java.io.BufferedOutputStream;

public interface MessagingProtocol<T> {
 
    /**
     * process the given message 
     * @param msg the received message
     * @param out the received output stream for communicating with the server
     * @return the response to send or null if no response is expected by the client
     */
    void process(T msg , BufferedOutputStream out);
 
    /**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
 
}