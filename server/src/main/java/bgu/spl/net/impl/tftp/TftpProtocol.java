package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;


class holder{
    static ConcurrentHashMap<Integer,Boolean> connected_ids = new ConcurrentHashMap<>();
    //a threadSafe HashMap that will hold the usernames as byte arrays that were already loggedin for their connectionID
    static ConcurrentHashMap<String, Boolean> loggedInUsernames = new ConcurrentHashMap<>();
    //a list of the files that are in the middle of an uploading proccess
    static List<String> currentlyUploading = new ArrayList<>();
    // static List<String> currentlyDownloading = new ArrayList<>();



}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private String username;
    private String fileNameAsStringToGetFromClient;
    private String fileNameAsStringToSendToClient;
    private FileOutputStream fos; 
    private FileInputStream fis; 
    private short currBlockNumber = 1; // Starting block number
    private boolean isDownloading = false;
    private boolean isSendingDIRQ = false;
    private int currentOffset = 0; // Tracks where in the dirqData array we are




    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId=connectionId;
        this.connections=connections;
        this.username= "";
        this.fileNameAsStringToGetFromClient = "";
        this.fileNameAsStringToSendToClient = "";
        


    }

    @Override
    public void process(byte[] message, BufferedOutputStream out) {
        // System.out.println("entered the server.protocol.procces of connectionID: " + connectionId +"\n" +
        // "and packeta is: " + Arrays.toString(message));
        
        int opcode= message[1];
        
        //for the LOGRQ case
        if(opcode == 7){
            LOGRQhandler(message);
        }

        //for the DISC case
        else if(opcode == 10){
            DISChandler(out);
        }
        
         //for the DELRQ case
        else if(opcode == 8){
            if(holder.loggedInUsernames.containsKey(username))
            DELRQhandler(message);
            else{
                sendErrorToClient((byte)6, connectionId , "User not logged in");
            }
        }
        //for the ACKF case
        else if(opcode == 4){
            ACKhandler(message,out);
            }

        //for the DIRQ case
        else if(opcode == 6){
            if(holder.loggedInUsernames.containsKey(username)){
                this.isSendingDIRQ=true;
                DIRQhandler(out);
            }
            else{
                sendErrorToClient((byte)6, connectionId , "User not logged in");
            }
            
            
        }
          //for the WRQ case
        else if(opcode == 2){
            if(holder.loggedInUsernames.containsKey(username))
            WRQhandler(message);
            else{
                sendErrorToClient((byte)6, connectionId , "User not logged in");
            }
        }
          //for the RRQ case
        else if(opcode == 1){
            if(holder.loggedInUsernames.containsKey(username))
            RRQhandler(message ,out);
            else{
                sendErrorToClient((byte)6, connectionId , "User not logged in");
            }
        }
        //for the DATA case (will only happen after WRQ in the server side)
        else if(opcode == 3){
        if(holder.loggedInUsernames.containsKey(username))
        DATAhandler(message);
        else{
            sendErrorToClient((byte)6, connectionId , "User not logged in");
        }
        
    }
        else{ //Illegal TFTP operation
            sendErrorToClient((byte)4, connectionId , "Illegal TFTP operation - Unknown Opcode.");
    }


    // System.out.println(connections.toString() +"and Exits procces() in server protocol");

    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;  
    } 



    private void LOGRQhandler(byte[] packeta){

        byte[] usernameBytes = new byte[packeta.length-2];
        System.arraycopy(packeta, 2, usernameBytes, 0, usernameBytes.length);

        // Convert the username bytes to a String
        String username = new String(usernameBytes, StandardCharsets.UTF_8);

        if(holder.connected_ids.get(connectionId)!=null){
            //send(using connections send) error with value 0
            // System.out.println("this client already logged in today");
            sendErrorToClient((byte)0,connectionId,"this client already logged in today");

        }
        else if (holder.loggedInUsernames.containsKey(username)) {
            //send(using connections send) error with value 7
            System.out.println("Username already logged in");
            sendErrorToClient((byte)7,connectionId,"Username already logged in");

            }
        else{
                holder.connected_ids.put(connectionId, true);
                holder.loggedInUsernames.put(username, true);
                this.username = username;
                // System.out.println(holder.loggedInUsernames.toString());
                // System.out.println(holder.connected_ids.toString());
                sendACK_0_toClient();
            }

    }

    private void DISChandler(BufferedOutputStream out){
        if(!holder.loggedInUsernames.containsKey(username))
            sendErrorToClient((byte)6, connectionId , "User not logged in");
        else{ // has logged in
            sendACK_0_toClient();
        }
        connections.disconnect(connectionId);
        holder.connected_ids.remove(connectionId);
        holder.loggedInUsernames.remove(username);
        this.username = "";
        
    }


    public void DIRQhandler(BufferedOutputStream out) {

        if(isSendingDIRQ){
            // System.out.println("Entered DIRQHandler");
        File directory = new File("Flies");
        File[] files = directory.listFiles();
        List<byte[]> fileNameBytesList = new ArrayList<>();
        int totalLength = 0;
        if (files != null) {
            // System.out.println("Files !-null if loop");
           for (File file : files) {
                if (!holder.currentlyUploading.contains(file.getName())) {
                    // Convert filename to byte array and add to the list
                    byte[] fileNameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
                    fileNameBytesList.add(fileNameBytes);
                    // Add length of the filename in bytes + 1 for the 0 byte separator
                    totalLength += fileNameBytes.length + 1;
                }
            }
        }
        byte[] dirqDataPacketToClient = new byte[totalLength];
        int currentPosition = 0;

        for (int i = 0; i < fileNameBytesList.size(); i++) {
            // System.out.println("List of files is : " +(fileNameBytesList.toString()));
            byte[] fileNameBytes = fileNameBytesList.get(i);
            // System.out.println("file Name Is : " + Arrays.toString(fileNameBytes));
            // Copy the filename bytes
            System.arraycopy(fileNameBytes, 0, dirqDataPacketToClient, currentPosition, fileNameBytes.length);
            currentPosition += fileNameBytes.length;
            // Add the 0 byte separator
            dirqDataPacketToClient[currentPosition] = 0;
            currentPosition += 1;
            
        }
        System.out.println("sending DirqDataPart: "+ Arrays.toString(dirqDataPacketToClient));
        sendDirqDataPacket(dirqDataPacketToClient,out);

        }
        
    }

    private void WRQhandler(byte[] packeta){
        byte[] fileNameBytes = new byte[packeta.length-2];
        System.arraycopy(packeta, 2, fileNameBytes, 0, fileNameBytes.length);

        // Convert the username bytes to a String
        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        this.fileNameAsStringToGetFromClient = fileName;

        String directory = "Flies";
        Path filePath = Paths.get(directory,File.separator ,fileName);
        // Check if the file exists
        if (Files.exists(filePath)) {
        // File exist, send error to client
        // System.out.println("sent the error with code 5 to the client because file already exist");
        sendErrorToClient((byte)5, connectionId, "File " + fileName + " already exist");
        }
        else{
            try {
                Files.createFile(filePath); // Creates an empty file with the name
                holder.currentlyUploading.add(fileName);
                sendACK_0_toClient(); // Send ACK to indicate readiness to receive DATA packets
            } catch (IOException e) {
                e.printStackTrace();
                sendErrorToClient((byte)0, connectionId, "Failed to create file");
            }
        }
        // System.out.println("Exited the WRQhandler()");
    }



    private void RRQhandler(byte[] packeta , BufferedOutputStream out){
            
            if(!isDownloading){
                //check if file already exists
                byte[] fileNameBytes = new byte[packeta.length-2];
                System.arraycopy(packeta, 2, fileNameBytes, 0, fileNameBytes.length);
        
                // Convert the username bytes to a String
                String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                this.fileNameAsStringToSendToClient = fileName;
        
                String directory = "Flies";
                Path filePath = Paths.get(directory,File.separator, fileName);
                // Check if the file exists
                if (!Files.exists(filePath)) {
                // File doesnt exist, send error to client
                // System.out.println("sent the error with code 1 to the client because file doesnt exist");
                sendErrorToClient((byte)1, connectionId, "File " + fileName + " not found");
                }
                else{
                    this.isDownloading = true;
                    sendDataPacket(fileNameAsStringToSendToClient, out);
                }
            }
            else{
                sendDataPacket(fileNameAsStringToSendToClient, out);
            }
            


 
    }


    
    private void DATAhandler(byte[] packeta){
        byte[] dataInBytes = new byte[packeta.length-6];
        System.arraycopy(packeta, 6, dataInBytes, 0, dataInBytes.length);

        // push the data bytes to the file
        String directory = "Flies";
        Path filePath = Paths.get(directory,File.separator, this.fileNameAsStringToGetFromClient);
        try {
            // Open the file output stream on first data packet if it's not already opened
            if (this.fos == null) {
                this.fos = new FileOutputStream(filePath.toFile(), true); // Open in append mode
            }
    
            // Write the data from the DATA packet to the file
            fos.write(dataInBytes);


            short blockNumber =  ( short ) ((( short ) packeta [4]) << 8 | ( short ) ( packeta [5]) & 0x00ff);

            sendACK(blockNumber); 
    
            // If this is the last packet (data length < 512), close the file output stream
            if (dataInBytes.length < 512) {
                byte[] fileNameInBytes = this.fileNameAsStringToGetFromClient.getBytes(StandardCharsets.UTF_8);
                sendBCAST((byte)1, fileNameInBytes);
                initializeWRQvariables();
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception, possibly sending an error packet back to the client
            sendErrorToClient((byte)0, connectionId, "Failed to write data to file");
        }
    }

    private void DELRQhandler(byte[] packeta){
        
        byte[] fileNameBytes = new byte[packeta.length-2];
        System.arraycopy(packeta, 2, fileNameBytes, 0, fileNameBytes.length);

        // Convert the username bytes to a String
        String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

        String directoryPath = "Flies";
        Path filePath = Paths.get(directoryPath,File.separator, fileName);

        if(holder.currentlyUploading.contains(fileName)){//dealing with trying to delete in-uploading files 
            sendErrorToClient((byte)2, connectionId , "File cannot be deleted because he is not fully uploaded");
            return;
        }
        // Check if the file exists
        if (!Files.exists(filePath)) {
        // File does not exist, send error to client
        // System.out.println("sent the error with code 1 to the client because file not found");
        sendErrorToClient((byte)1, connectionId, "File " + fileName + " not found");
        }
        else{
                sendACK_0_toClient();
                try {
                    Files.delete(filePath);
                } catch (IOException e) {
                    System.out.println("Error deleting file: " + e.getMessage());
                };
                sendBCAST((byte)0,fileNameBytes);
            }
            
    }
    

    private void initializeWRQvariables(){
        try {
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        holder.currentlyUploading.remove(fileNameAsStringToGetFromClient);
        this.fileNameAsStringToGetFromClient = "";
        fos = null;

    }
   


    private void sendACK_0_toClient(){
        byte[] ACKmessage = new byte[]{0,4,0,0};
        connections.send(connectionId,ACKmessage);
        // System.out.println("ACK was sent to client "+connectionId);
    }


    private void sendACK(short blockNumber){
        byte [] blockNumberAsBytes = new byte []{( byte ) ( blockNumber >> 8) , ( byte ) ( blockNumber & 0xff ) };
        byte[] ACKmessage = new byte[4];
        ACKmessage[0]=0;
        ACKmessage[1]=4;
        ACKmessage[2]=blockNumberAsBytes[0];
        ACKmessage[3]=blockNumberAsBytes[1];
        connections.send(connectionId,ACKmessage);
        // System.out.println("ACK was sent to client "+connectionId);
    }


    
    private void sendBCAST(byte deletedOrAdded, byte[] fileNameinBytes){
        byte[] BCASTstartOfPacket = new byte[]{0,9,deletedOrAdded};
        byte[] BCASTpacket = new byte[3 + fileNameinBytes.length + 1];
        System.arraycopy(BCASTstartOfPacket, 0, BCASTpacket, 0, BCASTstartOfPacket.length);
        System.arraycopy(fileNameinBytes, 0, BCASTpacket, BCASTstartOfPacket.length, fileNameinBytes.length);
        BCASTpacket[BCASTpacket.length - 1] = 0;


            //we will have to deal with sycronization for Bcast. 
            for(Integer id : holder.connected_ids.keySet()){
                connections.send(id,BCASTpacket);
                // System.out.println("BCAST was sent to client ID: "+ id);

            }
            System.out.println("BCAST was sent to all clients");


    }



      



    private void sendErrorToClient(byte errorCode,int id, String errMsg){
        byte[] errorStartOfThePacket = new byte[]{0,5,0,errorCode};
        byte[] errorMessage = errMsg.getBytes(StandardCharsets.UTF_8);
        byte[] errorPacket= new byte[errorMessage.length + errorStartOfThePacket.length +1];
        // Copy the start of the packet
        System.arraycopy(errorStartOfThePacket, 0, errorPacket, 0, errorStartOfThePacket.length);
        
        // Copy the error message
        System.arraycopy(errorMessage, 0, errorPacket, errorStartOfThePacket.length, errorMessage.length);
        
        // Add a 0 byte at the end
        errorPacket[errorPacket.length - 1] = 0;
        
        // Send the constructed error packet
        connections.send(id, errorPacket);
        // System.out.println("Error was sent to the client");
    }


    public void sendDataPacket(String fileName, BufferedOutputStream out) {
        // while (isDownloading) {

            try  {
                // System.out.println("SERVER entered SendDATApacket()");
                if(currBlockNumber == 1){//only for first data upload we will initialize the fis
                    // System.out.println("entered the if cuurblocknum =1");
                    String filePathNew = "Flies" + File.separator + fileName;
                    File filePathToSend = new File(filePathNew);
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
                    // System.out.println("sending the client this packetbuffer: " + packetBuffer.array().toString());
                    // Increase block number for the next packet
                    currBlockNumber++;
                    // If less than 512 bytes were read, it's the last packet
                    if (bytesRead < 512) {
                        initializeRRQVariables();
                    }
                    out.write(packetBuffer.array(), 0, packetBuffer.position());
                    out.flush(); 
                    
                }
                else if((fis.read(buffer)) == -1) { //for data size = 0
                    // System.out.println("entered if bytesRead ==-1");
                    // Prepare the DATA packet
                    ByteBuffer packetBuffer = ByteBuffer.allocate(6);
                    packetBuffer.putShort((short) 3); // Opcode for DATA is 3
                    packetBuffer.putShort((short) 0);
                    packetBuffer.putShort(currBlockNumber);
        
                    // Send the DATA packet
                    // System.out.println("size of data is : " + 0);
                    // System.out.println("sending the client this packetbuffer: " + packetBuffer.array().toString());
                    // Increase block number for the next packet
                    // If less than 512 bytes were read, it's the last packet
                    initializeRRQVariables();
                    out.write(packetBuffer.array(), 0, packetBuffer.position());
                    out.flush(); 
                    
                }
            } catch (IOException e) {
                e.printStackTrace();
            // }
        }
    }



    public void sendDirqDataPacket(byte[] dirqData, BufferedOutputStream out) {
        // while (isSendingDIRQ) {

            try  {
                // System.out.println("SERVER entered SendDIRQpacket()");              

                int remainingBytes = dirqData.length - currentOffset;
                short numOfBytesInTheDataPart = remainingBytes < 512 ? (short) remainingBytes : 512; // Determine the size of this packet's data part
                // Prepare the DATA packet
                ByteBuffer packetBuffer = ByteBuffer.allocate(6 + numOfBytesInTheDataPart);
                packetBuffer.putShort((short) 3); // Opcode for DATA is 3
                packetBuffer.putShort(numOfBytesInTheDataPart);
                packetBuffer.putShort(currBlockNumber);
                packetBuffer.put(dirqData, currentOffset, numOfBytesInTheDataPart);
    
                currentOffset += numOfBytesInTheDataPart;

                // Send the DATA packet
                // System.out.println("size of data is : " + numOfBytesInTheDataPart);
                // System.out.println("sending the client this packetbuffer: " + Arrays.toString(packetBuffer.array()));
                // Increase block number for the next packet
                currBlockNumber++;
                // If less than 512 bytes were read, it's the last packet
                if (numOfBytesInTheDataPart < 512 || currentOffset >= dirqData.length) {
                    this.isSendingDIRQ = false;
                    this.currBlockNumber = 1;
                    this.currentOffset = 0;
                }
                out.write(packetBuffer.array(), 0, packetBuffer.position());
                out.flush(); 
                
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        // }
    }

    private void ACKhandler(byte[] packeta, BufferedOutputStream out) {
        byte[] blockNumberInBytes = new byte[2];
        System.arraycopy(packeta, 2, blockNumberInBytes, 0, blockNumberInBytes.length);
        //function that was given to us
        short blockNumber = ( short ) ((( short ) blockNumberInBytes [0]) << 8 | ( short ) ( blockNumberInBytes [1]) & 0x00ff);
        System.out.println("ACK " + blockNumber);
        if(isDownloading)//for RRQ
            RRQhandler(packeta, out);//it won't use the packet but must get one
        if(isSendingDIRQ)
            DIRQhandler(out); //it won't use the packet but must get one
    }


    public void initializeRRQVariables() {
        this.fileNameAsStringToSendToClient = "";
        try {
            this.fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.fis = null;
        this.currBlockNumber = 1; // Starting block number
        this.isDownloading = false;
    }

       
    
}