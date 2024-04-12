package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private int length = 0;
    private final ByteBuffer opcodeBuffer = ByteBuffer.allocate(2); // Buffer for opcode
    private int opcodeBytesReceived = 0; 
    private boolean isOpcodeComplete = false;
    private int opcode =-1;
    private final ByteBuffer dataPacketSizeBuffer = ByteBuffer.allocate(2); // Buffer for opcode
    private int dataPacketSize =512;//max value


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (!isOpcodeComplete) {
                opcodeBuffer.put(nextByte);
                opcodeBytesReceived++;
                buffer.put(nextByte);
                length++;
                
                
                if (opcodeBytesReceived == 2) { // We've received both bytes of the opcode
                    isOpcodeComplete = true;
                    opcodeBuffer.flip(); // Prepare to read from the buffer
                    opcode = opcodeBuffer.getShort(); // Read the opcode as a short
                    opcodeBuffer.clear(); // Clear for next use
                    opcodeBytesReceived = 0; // Clear for next use

                    if(opcode==6){//DIRQ
                        //will never get more then 2 bytes
                        return handleDIRQPackets();
                    }

                    if(opcode==10){//DISC
                        //will never get more than 2 bytes
                        return handleDISCPackets();
                    }

            }
            return null;
        }
        else{ //OpCode is complete and we have it
            if(opcode == 3){//DATA
                return handleDATAPackets(nextByte);
            }
            if(opcode==4){//Acknowledgment
                return handleACKPackets(nextByte);
            }
            if(opcode==5){//ERROR
                return handleErrorPackets(nextByte);
            }
           
            if(opcode==9){//Bcast
                return handleBCASTPackets(nextByte);
            }
            else{
                return handleStringPackets(nextByte);
            } 
        }    
    }

    @Override
    public byte[] encode(byte[] message) {
        return message; // maybe we will have to add the last 0
    }


    private byte[] handleStringPackets(byte nextByte) {
        if(nextByte == '\0') { //The array of bytes ends with a 0 byte
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);            
            resetEncDec();
            return completeMessage;
        } else {
            buffer.put(nextByte);
            length++;
            return null; // Indicate that more bytes are needed
        }
    }

    private byte[] handleErrorPackets(byte nextByte) {
        if(length>4 && nextByte == '\0') { //when bigger then 4 no more 0 bytes allowed
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);            
            resetEncDec();
            return completeMessage;
        } else {
            buffer.put(nextByte);
            length++;
            return null; // Indicate that more bytes are needed
        }
    }

    private byte[] handleDIRQPackets() {
            //will just return 2 bytes
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);            
            resetEncDec();
            return completeMessage;
    }

    private byte[] handleDISCPackets() {
        //will just return 2 bytes
        byte[] completeMessage = new byte[length];
        buffer.flip();
        buffer.get(completeMessage);            
        resetEncDec();
        return completeMessage;
}


    
    private byte[] handleBCASTPackets(byte nextByte) {
        if(length>3 && nextByte == '\0') { //when bigger then 3 no more 0 bytes allowed
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);          
            resetEncDec();
            return completeMessage;
        } else {
            buffer.put(nextByte);
            length++;
            return null; // Indicate that more bytes are needed
        }
    }

    private byte[] handleDATAPackets(byte nextByte) {
        //we need to understand the stopping condition
        if(length<4){
            dataPacketSizeBuffer.put(nextByte);
        }
        if(length==4){//get the size of the data packet
            dataPacketSizeBuffer.flip();
            dataPacketSize = dataPacketSizeBuffer.getShort();
            dataPacketSizeBuffer.clear();
        }
        if(!(length == dataPacketSize+6)){
            buffer.put(nextByte);
            length++;
            // System.out.println(length);
        }
        if(length == dataPacketSize+6) {
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);          
            resetEncDec();
            return completeMessage;
        }
        return null; // Indicate that more bytes are needed
    }

    private byte[] handleACKPackets(byte nextByte) {
        if(length == 3) { //The array of bytes will allways be 4 bytes long
            buffer.put(nextByte);
            length++;
            byte[] completeMessage = new byte[length];
            buffer.flip();
            buffer.get(completeMessage);            
            resetEncDec();
            return completeMessage;
        } else {
            buffer.put(nextByte);
            length++;
            return null; // Indicate that more bytes are needed
        }
    }
    
    private void resetEncDec() {
        length = 0;
        buffer.clear();
        isOpcodeComplete = false;
        opcode =-1; 
        dataPacketSize = 512;
       }


}



