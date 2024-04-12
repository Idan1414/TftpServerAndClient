package bgu.spl.net.impl.tftp;


import bgu.spl.net.srv.Server;

public class TftpServer {
       public static void main(String[] args) {
        if(args.length < 1){
            System.out.println("      ERROR ! : Please write a PORT number like 7777");
            return;
        }

        int port = Integer.parseInt(args[0]);
        // you can use any server... 
        Server.threadPerClient(
                port, //port
                () -> new TftpProtocol(), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();

    }
}