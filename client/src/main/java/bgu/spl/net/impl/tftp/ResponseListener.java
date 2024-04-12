package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;


public class ResponseListener implements Runnable {
    private final BufferedInputStream in; // data that will come from the server in type byte[]
    private final BufferedOutputStream out;
    private TftpClientProtocol protocol;
    private TftpClientEncoderDecoder encdec;
    private Object lock;


     public ResponseListener(BufferedInputStream in ,BufferedOutputStream out,Object lock, TftpClientProtocol _protocol ) {
        this.in = in;
        this.out = out;
        this.protocol = _protocol;
        this.encdec = new TftpClientEncoderDecoder();
        this.lock = lock;
    }

    @Override
    public void run() {

        try {
            int read;
            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                // System.out.println(nextMessage);
                if (nextMessage != null) {
                    protocol.process(nextMessage , out);
                    if(!protocol.isDownloading()&& !protocol.isUploading()){
                        synchronized(lock) { // Ensure this block synchronizes on the lock object
                            lock.notifyAll();
                            // System.out.println("ClientListener notified the CommandSender to wake up");
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
