package bgu.spl.net.impl.tftp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class CommandSender implements Runnable {
    private final OutputStream out; // To send data to the server
    private final BufferedReader userInput; // To read user input from the console
    private Object lock;
    private boolean shouldTerminate = false;
    private TftpClientProtocol protocol;



    public CommandSender(OutputStream out,Object lock ,TftpClientProtocol _protocol) {
        this.out = out;
        this.userInput = new BufferedReader(new InputStreamReader(System.in));
        this.lock = lock;
        this.protocol = _protocol;
    }

    @Override
    public void run() {
        try {
                String inputLine;
                while (!shouldTerminate) {
                        // System.out.print("please write a command here: "); // Prompt the user
                        inputLine = userInput.readLine(); // Wait and read the user input
                        
                        if (inputLine == null) {
                            break; // Exit loop if input is null (e.g., EOF or Ctrl+D)
                        }
                        synchronized (lock) {
                        String[] parts = inputLine.split("\\s+", 2); // Split the input line into two parts: command and the rest
                        String command = parts[0];                
                        byte[] commandBytes = convertCommandToPacketa(command, parts.length > 1 ? parts[1] : "");
                        if (commandBytes != null) {
                            out.write(commandBytes);
                            out.flush();
                            // System.out.println("CommandSender went to sleep");
                            lock.wait();
                            // System.out.println("Client commandSender has woke up and souldTerminate is " +shouldTerminate );
                            
                            if(shouldTerminate){
                                protocol.terminate();
                                System.out.println("Cliet Protocol terminated ");
                            } 
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error handling user commands: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Command sender interrupted.");
            }
        }
    
        private byte[] convertCommandToPacketa(String command, String argument) {    
            if (command.equals("LOGRQ") ) {
                byte[] byteArrayToReturn = new byte[2+argument.length()+1] ; // 2 is for opcode 1 is for the 0 at the end
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 7;

                if(argument.length() != 0) {
                    byte[] argumentInBytes = argument.getBytes();
                    //adding the argumentInBytes to the byteArrayToReturn
                    System.arraycopy(argumentInBytes, 0, byteArrayToReturn, 2, argumentInBytes.length);
                    // Add a '0' byte at the end
                    byteArrayToReturn[byteArrayToReturn.length-1] = 0;
                }
                return byteArrayToReturn;
            }

            if (command.equals("DELRQ") ) {
                byte[] byteArrayToReturn = new byte[2+argument.length()+1] ; // 2 is for opcode 1 is for the 0 at the end
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 8;

                if(argument.length() != 0) {
                    byte[] argumentInBytes = argument.getBytes();
                    //adding the argumentInBytes to the byteArrayToReturn
                    System.arraycopy(argumentInBytes, 0, byteArrayToReturn, 2, argumentInBytes.length);;
                    // Add a '0' byte at the end
                    byteArrayToReturn[byteArrayToReturn.length - 1] = 0;
                }
                return byteArrayToReturn;
            }

            if (command.equals("RRQ") ) {
                byte[] byteArrayToReturn = new byte[2+argument.length()+1] ; // 2 is for opcode 1 is for the 0 at the end
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 1;

                if(argument.length() != 0) {
                   
                    //check if file already exists
                    String relativePathToFile ="."+File.separator + argument; //the argument is the file PATH
                    File file = new File(relativePathToFile);
                    if(file.exists()){
                        System.out.println("file already exists");
                        return null;
                    }
                    //if file doesn't exists
                    Path filePathWithFileName = Paths.get(relativePathToFile);
                        try {
                            Files.createFile(filePathWithFileName); // Creates an empty file with the name
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    byte[] argumentInBytes = argument.getBytes();
                    //adding the argumentInBytes to the byteArrayToReturn
                    System.arraycopy(argumentInBytes, 0, byteArrayToReturn, 2, argumentInBytes.length);;
                    // Add a '0' byte at the end
                    byteArrayToReturn[byteArrayToReturn.length - 1] = 0;
                }
                protocol.SetIsDownloading(true);
                protocol.SetFileNameForDownloadOrUpload(argument.getBytes());
                return byteArrayToReturn;
            }

            if (command.equals("WRQ") ) {
                byte[] byteArrayToReturn = new byte[2+argument.length()+1] ; // 2 is for opcode 1 is for the 0 at the end
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 2;
                byte[] argumentInBytes = {};
                if(argument.length() != 0) {
                     //check if file exists in order to avoid exeption
                    String relativePathToFile = "."+File.separator + argument;
                     File file = new File(relativePathToFile);
                     if(!file.exists()){
                         System.out.println("File doesn't exists in client side");
                         return null;
                     }
                    argumentInBytes = argument.getBytes();
                    //adding the argumentInBytes to the byteArrayToReturn
                    System.arraycopy(argumentInBytes, 0, byteArrayToReturn, 2, argumentInBytes.length);;
                    // Add a '0' byte at the end
                    byteArrayToReturn[byteArrayToReturn.length - 1] = 0;
                }
                protocol.SetIsUploading(true);
                protocol.SetFileNameForDownloadOrUpload(argumentInBytes);
                return byteArrayToReturn;
            }


            if (command.equals("DIRQ") ) {
                byte[] byteArrayToReturn = new byte[2] ;
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 6;
                return byteArrayToReturn;
            }

            if (command.equals("DISC") ) {
                byte[] byteArrayToReturn = new byte[2] ;
                byteArrayToReturn[0] = 0;
                byteArrayToReturn[1] = 10;
                shouldTerminate = true;
                return byteArrayToReturn;
            }

            else{
                System.out.println("Please right a valid command");
                return null;
            }
        }
    }