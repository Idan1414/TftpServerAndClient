package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import bgu.spl.net.api.MessagingProtocol;


public class TftpClientProtocol implements MessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private boolean isFirstAck = true;
    private boolean isLogin = false;
    private boolean isDownloading = false;
    private boolean isUploading = false;
    private boolean finishedWRQprocces = false;
    private boolean gotBCASTwhileTransferData = false;
    private String fileNameAsStringToDownloadOrUpload = "";
    private FileInputStream fis; // Class variable for FileInputStream
    private FileOutputStream fos; // Class variable for FileOutputStream
    private short currBlockNumber = 1; // Starting block number
    private ByteArrayOutputStream fileNameDataAccumulator = new ByteArrayOutputStream();
    private byte[] BcastPacketToStoreUntilFinishTransfer ={}; // to store for the case of Bcast while transfering 









    @Override
    public void process(byte[] message,BufferedOutputStream out) {
        // System.out.println("entered the Client.protocol.procces of client"+
        // " and packeta is: " + Arrays.toString(message));
        
        
        int opcode= message[1];
        
        
        if(opcode == 3){
            if(isDownloading|isUploading)
                DATAhandler(message,out);
            else{
                DIRQDATAhandler(message,out);
            }
        }



        if(opcode == 4){
            ACKhandler(message);
            if(finishedWRQprocces){
                System.out.println("WRQ "+fileNameAsStringToDownloadOrUpload + " complete");
                initializefileVariables();

            }
            else if(isUploading){
                sendDataPacket(fileNameAsStringToDownloadOrUpload, out);
            }

        }
        if(opcode == 5){
            ERRORhandler(message);
        }
        if(opcode == 9){
            if(!isDownloading && !isUploading ){
                BCASThandler(message);
            }
            else{//client is transfering data
                this.gotBCASTwhileTransferData = true;
                this.BcastPacketToStoreUntilFinishTransfer = message;
            }
        }

      
        
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;  
    } 

    public boolean isLogin() {
        return isLogin;  
    } 

    public boolean isDownloading() {
        return isDownloading;  
    } 

    public void  SetIsDownloading(boolean TorF) {
         isDownloading = TorF;  
    } 

    public void  SetFinishedWRQ(boolean TorF) {
        finishedWRQprocces = TorF;  
   } 

    public boolean isUploading() {
        return isUploading;  
    } 

    public void  SetIsUploading(boolean TorF) {
        isUploading = TorF;  
   } 


   public void SetFileNameForDownloadOrUpload(byte[] fileNameinBytes){
        this.fileNameAsStringToDownloadOrUpload = new String(fileNameinBytes, StandardCharsets.UTF_8);
   }



    private void ACKhandler(byte[] packeta) {
        byte[] blockNumberInBytes = new byte[2];
        System.arraycopy(packeta, 2, blockNumberInBytes, 0, blockNumberInBytes.length);
        //function that was given to us
        short blockNumber = ( short ) ((( short ) blockNumberInBytes [0]) << 8 | ( short ) ( blockNumberInBytes [1]) & 0x00ff);
        if(isFirstAck){//first time of acknowlegment will be the LOGRQ response.
            isLogin = true;
            isFirstAck = false;
        }
        System.out.println("ACK " + blockNumber);
        
    }



    private void ERRORhandler(byte[] packeta) {
        byte[] errorCodeInBytes = new byte[2];
        byte[] errorMsgInBytes = new byte[packeta.length-4];

        System.arraycopy(packeta, 2, errorCodeInBytes, 0, errorCodeInBytes.length);
        System.arraycopy(packeta, 4, errorMsgInBytes, 0, errorMsgInBytes.length);
        String errorMessage = new String(errorMsgInBytes, StandardCharsets.UTF_8); // converting bytes to string
        //function that was given to us
        short errorCode = ( short ) ((( short ) errorCodeInBytes [0]) << 8 | ( short ) ( errorCodeInBytes [1]) & 0x00ff);
        System.out.println("Error " + errorCode +" "+ errorMessage);

        if(errorCode == 5){ // if file already exist - stop the uploading procces and wake up the commandSender
            this.isUploading= false;
            this.fileNameAsStringToDownloadOrUpload = "";
        }

        if (errorCode ==1 || errorCode==6 ) { //for handling file things

            String filePathToDelete = "." + File.separator + fileNameAsStringToDownloadOrUpload;
            File fileToDelete = new File(filePathToDelete);
            if(fileToDelete.exists() && isDownloading) {
                boolean isDeleted = fileToDelete.delete();
                if(isDeleted) {
                    // System.out.println("File Skeleton (just the name) deleted successfully: " + fileNameAsStringToDownloadOrUpload);
                    initializefileVariables();
                } else {
                    // System.out.println("Failed to delete the file skeleton: " + fileNameAsStringToDownloadOrUpload);
                    initializefileVariables();
                }
            } else {
                // System.out.println("File does not exist: " + fileNameAsStringToDownloadOrUpload);
                initializefileVariables();
             }
        }
    }



    private void BCASThandler(byte[] packeta) {
        byte deletedOrAdded = packeta[2];
        byte[] fileNameBytes = new byte[packeta.length -3];
        //function that was given to us
        System.arraycopy(packeta, 3, fileNameBytes, 0, fileNameBytes.length);
        String fileNameAsString = new String(fileNameBytes, StandardCharsets.UTF_8); // converting bytes to string
        String delOrAdd ="";
        if(deletedOrAdded == (byte)0){
            delOrAdd = "del";
        }
        else{
            delOrAdd = "add";
        }
        System.out.println("BCAST " + delOrAdd + " " +fileNameAsString );
    }





    public void sendDataPacket(String fileName, OutputStream out) {
        try  {
            // System.out.println("entered SendDATApacket()");
            if(currBlockNumber == 1){//only for first data upload we will initialize the fis
                // System.out.println("entered the if cuurblocknum =1");
                File filePathToSend = new File(fileNameAsStringToDownloadOrUpload);
                this.fis = new FileInputStream(filePathToSend);
            }
            byte[] buffer = new byte[512]; // data part max size
            int bytesRead=0;
    
            if((bytesRead = fis.read(buffer)) > 0) {
                // System.out.println("entered if bytesRead>0");
                // Prepare the DATA packet
                ByteBuffer packetBuffer = ByteBuffer.allocate(6 + bytesRead);
                short numOfBytesInTheFIle = (short)bytesRead;
                packetBuffer.putShort((short) 3); // Opcode for DATA is 3
                packetBuffer.putShort(numOfBytesInTheFIle);
                packetBuffer.putShort(currBlockNumber);
                packetBuffer.put(buffer, 0, numOfBytesInTheFIle);
    
                // Send the DATA packet
                // System.out.println("size of data is : " + bytesRead);
                // System.out.println("sending the server this packetbuffer: " + packetBuffer.array().toString());
                 // Increase block number for the next packet
                currBlockNumber++;
                // If less than 512 bytes were read, it's the last packet
                 if (bytesRead < 512) {
                    SetFinishedWRQ(true);
                }
                out.write(packetBuffer.array(), 0, packetBuffer.position());
                out.flush(); 
                
            }
            else if((fis.read(buffer)) == -1) {//incase of exactly 512 bytes in the first packet and 0 in the second
                // System.out.println("entered if bytesRead == -1");
                // Prepare the DATA packet
                ByteBuffer packetBuffer = ByteBuffer.allocate(6);
                packetBuffer.putShort((short) 3); // Opcode for DATA is 3
                packetBuffer.putShort((short) 0);
                packetBuffer.putShort(currBlockNumber);    
                // Send the DATA packet
                // System.out.println("size of data is : " + 0);
                // System.out.println("sending the server this packetbuffer: " + packetBuffer.array().toString());
                // it's the last packet
                initializefileVariables();
                out.write(packetBuffer.array(), 0, packetBuffer.position());
                out.flush(); 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void terminate() {
        this.shouldTerminate = true;
    }


    public void initializefileVariables() {
        this.fileNameAsStringToDownloadOrUpload = "";
        this.isUploading = false;
        this.isDownloading = false;
        this.finishedWRQprocces = false;

        try {
            if(fis!=null)
                this.fis.close();
            if(fos !=null)
                this.fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.fis = null;
        this.fos = null;
        this.currBlockNumber = 1; // Starting block number
    }





    private void DATAhandler(byte[] packeta, OutputStream out){
        // System.out.println("Client entred dataHandler()");
        byte[] dataInBytes = new byte[packeta.length-6];
        System.arraycopy(packeta, 6, dataInBytes, 0, dataInBytes.length);
        // System.out.println(Arrays.toString(dataInBytes));
        // push the data bytes to the file
        Path filePathWithFileName = Paths.get("."+File.separator ,this.fileNameAsStringToDownloadOrUpload);
        try {
            // Open the file output stream on first data packet if it's not already opened
            // System.out.println(fos);
            if (this.fos == null) {
                this.fos = new FileOutputStream(filePathWithFileName.toFile(), true); // Open in append mode
            }
            // System.out.println(fos.toString());
            // Write the data from the DATA packet to the file
            fos.write(dataInBytes);
            short blockNumber =  ( short ) ((( short ) packeta [4]) << 8 | ( short ) ( packeta [5]) & 0x00ff);
            sendACK(blockNumber,out);
            // If this is the last packet (data length < 512), close the file output stream
            if (dataInBytes.length < 512) {
                if(isDownloading)//only in RRQ
                    System.out.println("RRQ "+fileNameAsStringToDownloadOrUpload+ " complete.");
                initializefileVariables();
                if(gotBCASTwhileTransferData){
                    BCASThandler(this.BcastPacketToStoreUntilFinishTransfer);
                    this.gotBCASTwhileTransferData = false;
                }
                
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendACK(short blockNumber , OutputStream out) throws IOException{
        byte [] blockNumberAsBytes = new byte []{( byte ) ( blockNumber >> 8) , ( byte ) ( blockNumber & 0xff ) };
        byte[] ACKmessage = new byte[4];
        ACKmessage[0]=0;
        ACKmessage[1]=4;
        ACKmessage[2]=blockNumberAsBytes[0];
        ACKmessage[3]=blockNumberAsBytes[1];
        out.write(ACKmessage);
        out.flush();
        // System.out.println("ACK was sent to Server ");
    }


    private void DIRQDATAhandler(byte[] packet,OutputStream out) {
        // Extract the data portion of the packet, skipping the first 6 bytes (2 for opcode, 2 for block number, and 2 for the data length)
        byte[] dataPart = Arrays.copyOfRange(packet, 6, packet.length);
    
        // Accumulate the data part
        try {
            fileNameDataAccumulator.write(dataPart);
            short blockNumber =  ( short ) ((( short ) packet [4]) << 8 | ( short ) ( packet [5]) & 0x00ff);
            sendACK(blockNumber,out);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    
        // If this packet's data part is less than 512, it's the last packet
        if (dataPart.length < 512) {
            // Split the accumulated data into file names using "0" byte as the delimiter
            byte[] accumulatedData = fileNameDataAccumulator.toByteArray();
            int start = 0; // Start index for each file name in the accumulated data
            
            //extract and print file names
            for (int i = 0; i < accumulatedData.length; i++) {
                if (accumulatedData[i] == 0) { // 0 byte found, indicating end of a file name
                    String fileName = new String(accumulatedData, start, i - start, StandardCharsets.UTF_8);
                    System.out.println(fileName); //DO NOT DELETE !!
                    start = i + 1; // Move to the start of the next file name
                }
            }
            // Reset the accumulator for future use
            fileNameDataAccumulator.reset();
        }
    }
    

    
}
