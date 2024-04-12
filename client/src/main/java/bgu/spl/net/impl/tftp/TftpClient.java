package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.lang.InterruptedException;

public class TftpClient {
        public static void main(String[] args) {
            if(args.length < 2){//default if no args
                System.out.println("Please enter 2 args - IP and PORT");
                return;
            }
            String serverAddress = args[0];
            int port = Integer.parseInt(args[1]);
            

            try (Socket sock = new Socket(serverAddress, port);
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())) {

            Object generalLock= new Object();
            TftpClientProtocol generaClientProtocol = new TftpClientProtocol();
            Thread senderThread = new Thread(new CommandSender(out,generalLock,generaClientProtocol));
            Thread listenerThread = new Thread(new ResponseListener(in, out,generalLock,generaClientProtocol));

            // Start both threads
            senderThread.start();
            listenerThread.start();

            // Wait for both threads to finish
            senderThread.join();
            listenerThread.join();
            System.out.println("Client threads terminated");

            } catch (IOException | InterruptedException e) {
                System.out.println("Client error: " + e.getMessage());
            }
    }
}