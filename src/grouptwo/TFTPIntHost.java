package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * TFTPIntHost spawns the CLI thread and a new ErrorSimulator thread for each
 * new client. Its behaviour is similar to TFTPServer in that the CLI thread
 * uses set methods in this class to create a list of modifications for the next
 * client thread. 
 *
 * @author        Cyrus Sadeghi
 */
public class TFTPIntHost 
{ 
    private DatagramSocket receiveSocket;
    private DatagramPacket receivePacket;
    private TFTPCommon.Verbosity verbosity;
    private TFTPIntHostCommandLine cliThread;
    private byte [] data;
    private List<SimulatePacketInfo> toModify;
    private Integer runningErrorSimCount;
    private InetAddress serverAddress;

    public TFTPIntHost()
    {
        try {
            receiveSocket = new DatagramSocket(TFTPCommon.TFTPErrorSimPort);
        } catch (SocketException se) {
            se.printStackTrace();
            System.exit(1);
        }

        verbosity = TFTPCommon.Verbosity.NONE;
        serverAddress = InetAddress.getLoopbackAddress();
        toModify = new ArrayList<SimulatePacketInfo>();
        runningErrorSimCount = 0;
        cliThread = new TFTPIntHostCommandLine(this);
    }

    /**
    *   Called by CLI thread, sets the verbosity for new ClientConnectionThreads
    *   Note: This doesn't change verbosity for ongoing transfers
    *
    *   @param  Verbosity verbosity to set to
    *   @return none
    */
    public void setVerbosity(TFTPCommon.Verbosity v) 
    {
        this.verbosity = v;
    }

    /**
     *   Add a new modification to the list of pending modifications
     *
     *   @param  SimulatePacketInfo mod to add
     *   @return Boolean true if successfully added
     */
    public Boolean appendMod(SimulatePacketInfo modification)
    {
        return toModify.add(modification);
    }

    /**
     *   Remove a pending modification form the list of pending modifications
     *
     *   @param  int index of modification to remove
     *   @return void
     */
    public void removeMod(int toRemove) throws IndexOutOfBoundsException
    {
        toModify.remove(toRemove);
    }

    /**
     *   Returns number of modifications pending
     *
     *   @param  none
     *   @return int size of list
     */
    public int simulateListLength()
    {
        return toModify.size();
    }

    /**
     *   Converts pending modification list to String
     *
     *   @param  none
     *   @return String
     */
    public String simulateListToString()
    {
        String returnString = new String();

        for (SimulatePacketInfo s : toModify)
        {
            returnString = returnString + s + System.lineSeparator();
        }

        return returnString;
    }

    /**
     *   Get list of pending packet modifications
     *   Used for removing pending mods in CLI
     *
     *   @param  void
     *   @return List<SimulatePacketInfo>
     */

    public List<SimulatePacketInfo> getSimulateList()
    {
        return toModify;
    }

    /**
     *   Set address of TFTP Server (if error sim and client are on same machine)
     *
     *   @param  InetAddress
     *   @return void
     */
    public void setAddress(InetAddress address)
    {
        serverAddress = address;
    }

    /**
     *   Creates a new ErrorSimulator thread for each new client, gives the new client
     *   the current pending list of modifications then resets the list. Similar implementation
     *   to TFTPServer's receiveClients.
     *
     *   @param  none
     *   @return none
     */
    private void processClients()
    {
        cliThread.start();

        System.out.println("Error Simulator: Waiting for clients.");

        while (true)
        {
            data = new byte[TFTPCommon.maxPacketSize];
            receivePacket = new DatagramPacket(data, data.length);

            try {
                receiveSocket.receive(receivePacket);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            runningErrorSimCount++;
            Thread client = new Thread(new ErrorSimulator(receivePacket, serverAddress, verbosity, toModify, runningErrorSimCount, this));
            client.start();
            toModify = new ArrayList<SimulatePacketInfo>();
        }
    }

    public static void main( String args[] )
    {
        TFTPIntHost s = new TFTPIntHost();
        s.processClients();
    }
}

/**
 * ErrorSimulator handles modifiying packets, each client connection gets an ErrorSimulator thread spawned
 * by TFTPIntHost. ErrorSimulator can delay, duplicate, lose or not modify packets that received from the server
 * or client.
 *
 * @author        Majeed Mirza
 * @author        Cyrus Sadeghi
 */
class ErrorSimulator extends Thread
{
    private DatagramSocket sendReceiveSocket, invalidTIDSocket;
    private DatagramPacket sendPacket, receivePacket;
    private TFTPCommon.Verbosity verbosity;
    private InetAddress clientAddress, serverAddress;
    private int clientPort, serverPort, len;
    private byte [] data;
    private SimulatePacketInfo simCompare;
    private List<SimulatePacketInfo> simulateList;
    private String consolePrefix;
    private TFTPIntHost parent;

    public ErrorSimulator(DatagramPacket firstPacket, InetAddress serverAddress, TFTPCommon.Verbosity verbose, List<SimulatePacketInfo> simulateList, Integer errorSimNum, TFTPIntHost intHost)
    {
        clientAddress = firstPacket.getAddress();
        clientPort = firstPacket.getPort();
        this.serverAddress = serverAddress;
        serverPort = 0;
        len = firstPacket.getLength();
        data = firstPacket.getData();
        receivePacket = firstPacket;
        verbosity = verbose;
        this.simulateList = simulateList;
        parent = intHost;
        consolePrefix = new String("Error Simulator Thread " + errorSimNum + ": ");
        
        try {
            sendReceiveSocket = new DatagramSocket();
            invalidTIDSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     *   Checks each packet that is received by the error sim against the list of modifications to perform.
     *   If the received packet is in the list, it is modified according to the list. Otherwise, the packet is
     *   passed on with no changes
     *
     *   @param  none
     *   @return none
     */
	private void errorSimulateSend() 
    {   
        for (SimulatePacketInfo check : simulateList)
        {   
            //Check the packet type and packet number (if applicable, not for request) against every modification in the list
            if ( TFTPCommon.getPacketType(receivePacket.getData()) == check.getPacketType() && ( (check.getPacketType() == TFTPCommon.PacketType.REQUEST) || (check.getPacketNum() == TFTPCommon.blockNumToPacket(receivePacket.getData()) ) ) )
            {
    			if (check.getModType() == TFTPCommon.ModificationType.LOSE) 
                {
    				losePacket(check);
                    simulateList.remove(check);
                    
                    //TODO - need to test losing request packets (as well as multiple clients)

                    //If we lose a request, the client will send another request which will spawn a new error sim thread
                    //The new thread won't have the list of modifications, so pass those now
                    if (check.getPacketType() == TFTPCommon.PacketType.REQUEST)
                    {
                        for (SimulatePacketInfo prop : simulateList)
                        {
                            parent.appendMod(prop);
                        }
                    }
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DUPLICATE || check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
                    Thread delayDuplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, sendReceiveSocket, check.getModType(), check.getDelayDuplicateGap()));
    				delayDuplicateThread.start();
                    simulateList.remove(check);
    			}

                else if (check.getModType() == TFTPCommon.ModificationType.CONTENTS)
                {
                    modifyContents(check);
                    simulateList.remove(check);
                }

                else if (check.getModType() == TFTPCommon.ModificationType.INVALIDTID)
                {
                    Thread delayDuplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, invalidTIDSocket, check.getModType(), 0));
                    delayDuplicateThread.start();
                    simulateList.remove(check);
                }

                else 
                {
    				System.out.println(consolePrefix + "Error on " + check.getPacketNum());
    			}

    			return;
            }
        }

		System.out.println(consolePrefix + "Sending " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " with no error simulation.");
        TFTPCommon.printPacketDetails(sendPacket, consolePrefix, verbosity, false);

		try {
	        sendReceiveSocket.send(sendPacket);
	    } catch (IOException e) {
	        e.printStackTrace();
	        System.exit(1);
	    }
	}

	/**
     *   Loses a packet, this is done by ignoring the packet (not sending it)
     *
     *   @param  SimulatePacketInfo modification information
     *   @return none
     */
    private void losePacket (SimulatePacketInfo check)
    {
        System.out.println(consolePrefix + "Lose " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()));
        TFTPCommon.printPacketDetails(sendPacket, consolePrefix, verbosity, false);
    }

    /**
     *   Modify contents of a packet, this method can modify file names/modes for request packets,
     *   length of a packet (decreasing length truncates it at new length, increasing length pads it with 0s),
     *   opcode, block number, as well as custom byte modifications
     *
     *   @param  SimulatePacketInfo modification information
     *   @return none
     */
    private void modifyContents (SimulatePacketInfo check)
    {
        data = sendPacket.getData();
        int len = sendPacket.getLength();
        String packetType = TFTPCommon.packetTypeAndNumber(sendPacket.getData());
        Boolean modFileName = check.subModListHas(TFTPCommon.ContentSubmod.FILENAME);
        Boolean modFileMode = check.subModListHas(TFTPCommon.ContentSubmod.FILEMODE);

        if ( modFileMode || modFileName )
        {
            String fileName, fileMode;
            int opcode, j, k;

            for (j = 2; j < sendPacket.getLength(); j++) 
            {
                if (data[j] == 0) 
                {
                    break;
                }
            }

            fileName = new String(data, 2, j - 2);
            
            for (k = j + 1; k < sendPacket.getLength(); k++)
            {
                if (data[k] == 0)
                {
                    break;
                }
            }

            fileMode = new String(data, j + 1, k - j - 1);
           
            opcode = Byte.toUnsignedInt(data[0]) * 256 + Byte.toUnsignedInt(data[1]);

            if ( modFileName && !check.getRmName() )
            {
                System.out.println(consolePrefix + "Changing file name in request from \"" + fileName + "\" to \"" + check.getFileName() + "\"");
                fileName = check.getFileName();
            }

            if ( modFileMode && !check.getRmMode() )
            {
                System.out.println(consolePrefix + "Changing file mode in request from \"" + fileMode + "\" to \"" + check.getFileMode() + "\"");
                fileMode = check.getFileMode();
            }

            data = new byte[TFTPCommon.maxPacketSize];
            len = TFTPCommon.constructReqPacket(data, opcode, fileName, fileMode, check.getRmName(), check.getRmMode());
            
            sendPacket.setData(data);
            sendPacket.setLength(len);
        }
        
        if (check.subModListHas(TFTPCommon.ContentSubmod.LENGTH))
        {
            data = sendPacket.getData();

            System.out.println(consolePrefix + "Changing " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " length from " + sendPacket.getLength() + " to " + check.getLength());

            //Packet's length is greater than new length, truncate packet
            if (sendPacket.getLength() > check.getLength())
            {
                System.arraycopy(data, 0, data, 0, check.getLength());
            }

            //Packet's length is less than new length, pad with 0s
            else
            {
                byte[] paddedData = new byte[check.getLength()];
                System.arraycopy(data, 0, paddedData, 0, sendPacket.getLength());
                data = paddedData;
            }

            len = check.getLength();
            sendPacket.setData(data);
            sendPacket.setLength(len);
        }

        for (ModifyByte mod : check.getModByteList())
        {
            if (mod.getPosition() < data.length)
            {
                System.out.println(consolePrefix + "Changing byte " + mod.getPosition() + " of " + packetType + " from " + data[mod.getPosition()] + " to " + mod.getValue());
                data[mod.getPosition()] = mod.getValue();
            }
            else
            {
                System.out.println(consolePrefix + "Ignoring content modification: byte # " + mod.getPosition() + " value " + mod.getValue() + " as modification is out of bounds for this packet");
            }
        }

        sendPacket.setData(data, 0, len);

        TFTPCommon.printPacketDetails(sendPacket, consolePrefix, verbosity, false);

        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     *   Receives packets from server and client, each packet is then checked by errorSimulateSend
     *   to determine if it requires modifications before passing the packet to its intended target
     *
     *   @param  none
     *   @return none
     */
    private void passOnTFTP()
    {
        int port = TFTPCommon.TFTPListenPort;
        InetAddress address = serverAddress;

        while (true) 
        {
            sendPacket = new DatagramPacket(data, len, address, port);
            len = sendPacket.getLength();
            TFTPCommon.printPacketDetails(receivePacket, consolePrefix, verbosity, false);

            errorSimulateSend();

            data = new byte[TFTPCommon.maxPacketSize];
                
            receivePacket = new DatagramPacket(data, data.length);

            try {
                sendReceiveSocket.receive(receivePacket);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            //Set server port if receivePacket is the first packet from the server
            if (serverPort == 0 && receivePacket.getPort() != clientPort)
            {
                serverPort = receivePacket.getPort();
            }

            //Setup variables to forward packet
            if (receivePacket.getPort() == serverPort)
            {
                port = clientPort;
                address = clientAddress;
            }
            else if (receivePacket.getPort() == clientPort)
            {
                port = serverPort;
                address = serverAddress;
            }
            else
            {
                System.out.println(consolePrefix + "Received packet from unknown port! This can happen with a delayed or duplicated request");
            }

            data = receivePacket.getData();
            len = receivePacket.getLength();
        }
    }

    public void run()
    {
        this.passOnTFTP();
    }
}

/**
 * DelayDuplicatePacket threads are spawned everytime a delay or duplicate needs to occur.
 * A new thread is required as delaying or duplicating packets causes the thread to go to sleep,
 * which would block any incoming packets from arriving.
 *
 * @author        Cyrus Sadeghi
 */
class DelayDuplicatePacket extends Thread
{
    private TFTPCommon.Verbosity verbosity;
    private DatagramPacket send, receive;
    private DatagramSocket socket;
    private TFTPCommon.ModificationType modType;
    private Integer delayAmount;
    private byte[] data;
    private String consolePrefix;

    public DelayDuplicatePacket(DatagramPacket sendPacket, DatagramSocket sendReceiveSocket, TFTPCommon.ModificationType mod, Integer delayDuplicateAmount)
    {
        if (mod != TFTPCommon.ModificationType.DUPLICATE && mod != TFTPCommon.ModificationType.DELAY && mod != TFTPCommon.ModificationType.INVALIDTID)
        {
            System.out.println("Invalid modification!");
            return;
        }

        send = sendPacket;
        data = new byte[TFTPCommon.maxPacketSize];
        receive = new DatagramPacket(data, data.length);
        socket =  sendReceiveSocket;
        delayAmount = delayDuplicateAmount;
        modType = mod;
        consolePrefix = "Error Simulator: ";
    }

    /**
     *   Send packet normally, wait for specified time, then send the packet again
     *
     *   @param  none
     *   @return none
     */
    public void duplicatePacket ()
    {
        System.out.println(consolePrefix + "Duplicate " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending first instance");
        
        TFTPCommon.printPacketDetails(send, consolePrefix, verbosity, false);
        
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println(consolePrefix + "Waiting " + delayAmount + " ms");
        
        try {
            Thread.sleep(delayAmount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(consolePrefix + "Duplicate " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending second instance");
        
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }   
    }
    
    /**
     *   Sleep for specified time, then send the packet again
     *
     *   @param  none
     *   @return none
     */
    public void delayPacket ()
    {
        System.out.println(consolePrefix + "Delay " + TFTPCommon.packetTypeAndNumber(send.getData()) + " by " + delayAmount + "ms");
        
        TFTPCommon.printPacketDetails(send, consolePrefix, verbosity, false);

        System.out.println(consolePrefix + "Waiting " + delayAmount + " ms");

        try {
            Thread.sleep(delayAmount);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        System.out.println(consolePrefix + "Delayed " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending now");
         
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } 
    }

    /**
     *   Send unmodified packet on new socket (invalid TID), receive and print error
     *
     *   @param  none
     *   @return none
     */
    public void sendReceiveInvalidTID ()
    {
        System.out.println(consolePrefix + "Sending " + TFTPCommon.packetTypeAndNumber(send.getData()) + " with invalid TID");

        TFTPCommon.printPacketDetails(send, consolePrefix, verbosity, false);

        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println(consolePrefix + "Waiting for error packet from server");

        try {
            socket.receive(receive);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println(consolePrefix + "Received " + TFTPCommon.packetTypeAndNumber(receive.getData()));

        TFTPCommon.printPacketDetails(receive, consolePrefix, verbosity, false);
    }

    public void run()
    {
        if (modType == TFTPCommon.ModificationType.DUPLICATE)
        {
            duplicatePacket();
        }
        else if (modType == TFTPCommon.ModificationType.DELAY)
        {
            delayPacket();
        }
        else if (modType == TFTPCommon.ModificationType.INVALIDTID)
        {
            sendReceiveInvalidTID();
        }
    }
}

/**
 * TFTPIntHostCommandLine is the command line for error simulator. It allows you to select packets to modify
 * and the type of modification (delaying, duplicating or losing) to apply to the packet. Multiple packets
 * can be modified for each client connection and modifications can be cancelled before they occur
 *
 * @author        Cyrus Sadeghi
 */
class TFTPIntHostCommandLine extends Thread
{
    private TFTPCommon.Verbosity verbosity;
    private TFTPIntHost parentSimulator;
    private InetAddress serverAddress = InetAddress.getLoopbackAddress();
    private Boolean cliRunning;
    private String scIn;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPCommon.Verbosity.NONE;
        cliRunning = true;
        scIn = new String();
    }

    /**
     *   Parse scanner String to int given limits on integer size, loop until input is a number
     *
     *   @param  Scanner scanner being used by CLI thread
     *   @param  int low limit for integer
     *   @param  int high limit for integer
     *   @param  String message to print when prompting for number
     *   @return int parsed
     */
    private int getIntMenu (Scanner sc, int lowLimit, int highLimit, int blackList, String promptMessage)
    {
        int parsedString = -1;

        while ( true )
        {
            System.out.print(promptMessage);

            scIn = sc.nextLine();

            try {
                parsedString = Integer.parseInt(scIn);
            } catch (NumberFormatException e) {
                System.out.println("Input was not a number, try again.");
            }

            if (parsedString < lowLimit || parsedString > highLimit)
            {
                System.out.println("Input out of range, try again.");
            }
            else if (parsedString == blackList)
            {
                System.out.println("Input invalid, try again");
            }
            else
            {
                return parsedString;
            }
        }
    }

    private void setServerAddress (Scanner sc)
    {
        System.out.print("Enter IP address: ");
        scIn = sc.nextLine();

        try
        {
            serverAddress = InetAddress.getByName(scIn);
            parentSimulator.setAddress(serverAddress);
        } 
        catch (UnknownHostException e)
        {
            System.out.println("Invalid IP address");
        }
    }

    private void deletePendingMod (Scanner sc)
    {
        int modToDelete;
        List<SimulatePacketInfo> modList;

        while ( true )
        {
            modList = parentSimulator.getSimulateList();

            if (modList.size() == 0)
            {
                System.out.println("There are no pending modifications");
                return;
            }

            scIn = new String();
            modToDelete = 0;

            System.out.println("List of Pending Modifications");

            for (int i = 0; i < modList.size(); i++)
            {
                System.out.println((i + 1) + ". " + modList.get(i));
            }

            while (modToDelete <= 0)
            {
                System.out.print("Enter a number (or r to return): ");

                scIn = sc.nextLine();

                if (scIn.equalsIgnoreCase("r"))
                {
                    return;
                }
                else
                {
                    try {
                        modToDelete = Integer.parseInt(scIn);
                    } catch (NumberFormatException e) {
                        System.out.println("Input wasn't a number, try again.");
                    }
                }
            }

            modToDelete--;

            if (modToDelete <= modList.size() && modList.size() == parentSimulator.simulateListLength())
            {
                try {
                    parentSimulator.removeMod(modToDelete);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Failed to remove mod!");
                }
            }
        }
    }

    /**
     *   Select packet type and packet number (if applicable) of packet to be modified
     *
     *   @param  Scanner being used by CLI thread
     *   @return SimulatePacketInfo containing only packet type and number, no modifications yet
     */
    private SimulatePacketInfo selectPacket(Scanner sc)
    {
        TFTPCommon.PacketType pType = TFTPCommon.PacketType.INVALID;
        int pNum = -1;
        int highLimit = 65535;
        int lowLimit = 0;
        int blackList = -1;
        String promptMessage = "Enter packet number: ";

        while (pType == TFTPCommon.PacketType.INVALID)
        {
            System.out.print("What packet type should be modified? (request, data, ack, error, or r to return): ");
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("request") )
            {
                pType = TFTPCommon.PacketType.REQUEST;
                pNum = 1;

                return new SimulatePacketInfo(pNum, pType);
            }
            else if ( scIn.equalsIgnoreCase("data") )
            {
                pType = TFTPCommon.PacketType.DATA;
            }
            else if ( scIn.equalsIgnoreCase("ack") )
            {
                pType = TFTPCommon.PacketType.ACK;
            }
            else if ( scIn.equalsIgnoreCase("error") )
            {
                pType = TFTPCommon.PacketType.ERROR;
                promptMessage = "Enter error code: ";
                lowLimit = 1;
                highLimit = 6;
                blackList = 5;
            }
            else if ( scIn.equalsIgnoreCase("r") )
            {
                return new SimulatePacketInfo(0, pType);
            }
            else if ( !scIn.equalsIgnoreCase("") )
            {
                System.out.println("Invalid packet type, try again.");
            }
        }

        pNum = getIntMenu(sc, lowLimit, highLimit, blackList, promptMessage);

        return new SimulatePacketInfo(pNum, pType);
    }

    /**
     *   Content modification sub menu, allows for modifiying opcode, filename/mode,
     *   block number, length and manual byte modifications. Adds the newly created modification
     *   to the list of packet modifications for the next client
     *
     *   @param  Scanner being used by CLI thread
     *   @return void
     */
    private void modifyContents(Scanner sc)
    {
        SimulatePacketInfo dataModPacket = selectPacket(sc);
        int opcode = -1;
        int blockNum = -1;
        int newLen = -1;

        if (dataModPacket.getPacketType() == TFTPCommon.PacketType.INVALID)
        {
            return;
        }

        while (true)
        {
            System.out.println("Packet Content Modifications");
            System.out.println("----------------------------");
            System.out.println("opcode: Modify opcode");
            
            if (dataModPacket.getPacketType() == TFTPCommon.PacketType.REQUEST)
            {
                System.out.println("fname: Modify file name");
                System.out.println("mode: Modify file mode");
                System.out.println("rmfname: Remove file name");
                System.out.println("rmmode: Remove file mode");
            }
            else if (dataModPacket.getPacketType() != TFTPCommon.PacketType.ERROR)
            {
                System.out.println("block: Modify block number");
            }
            else
            {
                System.out.println("num: Modify error number");
            }
            System.out.println("length: Modify packet length");
            System.out.println("manual: Manually modify packet contents");
            System.out.println("r: return without saving");
            System.out.println("s: save changes and return");

            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("opcode") )
            {
                opcode = getIntMenu(sc, 0, 512, -1, "Enter new opcode (as bytes, i.e. 02, 03, etc.): ");
                dataModPacket.setOpcode(opcode);
                dataModPacket.addContentModType(TFTPCommon.ContentSubmod.OPCODE);
            }
            
            if (dataModPacket.getPacketType() == TFTPCommon.PacketType.REQUEST)
            {
                if ( scIn.equalsIgnoreCase("fname") )
                {
                    System.out.print("Enter new file name: ");
                    dataModPacket.setFileName(sc.nextLine());
                    dataModPacket.addContentModType(TFTPCommon.ContentSubmod.FILENAME);
                }
                else if ( scIn.equalsIgnoreCase("mode") )
                {
                    System.out.print("Enter new file mode: ");
                    dataModPacket.setFileMode(sc.nextLine());
                    dataModPacket.addContentModType(TFTPCommon.ContentSubmod.FILEMODE);
                }
                else if ( scIn.equalsIgnoreCase("rmfname") )
                {
                    dataModPacket.removeName();
                    dataModPacket.addContentModType(TFTPCommon.ContentSubmod.FILENAME);
                }
                else if ( scIn.equalsIgnoreCase("rmmode") )
                {
                    dataModPacket.removeMode();
                    dataModPacket.addContentModType(TFTPCommon.ContentSubmod.FILEMODE);
                }
            }

            else if ( dataModPacket.getPacketType() != TFTPCommon.PacketType.ERROR && scIn.equalsIgnoreCase("block") )
            {
                blockNum = getIntMenu(sc, 0, 512, -1, "Enter new block number (as integer, 0-512): ");
                dataModPacket.setBlockNum(blockNum);
                dataModPacket.addContentModType(TFTPCommon.ContentSubmod.BLOCKNUM);
            }

            else if ( scIn.equalsIgnoreCase("num") )
            {
                //Reuse blockNum for ERROR packets as error number
                blockNum = getIntMenu(sc, 0, 512, -1, "Enter new error number (as integer, 0-512): ");
                dataModPacket.setBlockNum(blockNum);
                dataModPacket.addContentModType(TFTPCommon.ContentSubmod.BLOCKNUM);
            }

            if ( scIn.equalsIgnoreCase("length") )
            {
                System.out.println("Note: If the new length is greater than the old length, the packet gets padded with 0s. If the new length is smaller than the old length, the packet gets truncated");
                newLen = getIntMenu(sc, 0, TFTPCommon.maxPacketSize, -1, "Enter new length (as integer, 0-1000): ");
                dataModPacket.setLength(newLen);
                dataModPacket.addContentModType(TFTPCommon.ContentSubmod.LENGTH);
            }

            else if ( scIn.equalsIgnoreCase("manual") )
            {
                Integer position = getIntMenu(sc, 0, TFTPCommon.maxPacketSize, -1, "Enter byte position to modify (as integer, 0-1000): ");
                Integer value = getIntMenu(sc, 0, 255, -1, "Enter new value for byte: ");

                ModifyByte modByte = new ModifyByte(position, value.byteValue());
                dataModPacket.addModByte(modByte);
                dataModPacket.addContentModType(TFTPCommon.ContentSubmod.MANUAL);
            }

            if ( scIn.equalsIgnoreCase("r") )
            {
                return;
            }

            if ( scIn.equalsIgnoreCase("s") )
            {
                if (dataModPacket.getSubModListSize() != 0)
                {
                    if (!parentSimulator.appendMod(dataModPacket))
                    {
                        System.out.println("Failed to add modification!");
                    }
                }

                return;
            }
        }
    }

    /**
     *   Delay a packet. Selects a packet, then asks for a delay amount
     *
     *   @param  Scanner being used by CLI thread
     *   @return void
     */
    private void delayPacket(Scanner sc)
    {
        SimulatePacketInfo delayPacket = selectPacket(sc);
        int delayAmount = getIntMenu(sc, 0, 100000, -1, "Enter amount to delay packet (ms): ");
        
        delayPacket.setModType(TFTPCommon.ModificationType.DELAY);
        delayPacket.setDelayDuplicateGap(delayAmount);

        if (!parentSimulator.appendMod(delayPacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    /**
     *   Duplicate a packet. Selects a packet, then asks for a duplicate gap
     *
     *   @param  Scanner being used by CLI thread
     *   @return void
     */
    private void duplicatePacket(Scanner sc)
    {
        SimulatePacketInfo duplicatePacket = selectPacket(sc);
        int duplicateGap = getIntMenu(sc, 0, 100000, -1, "Enter gap between duplicated packets (ms): ");
        
        duplicatePacket.setModType(TFTPCommon.ModificationType.DUPLICATE);
        duplicatePacket.setDelayDuplicateGap(duplicateGap);

        if (!parentSimulator.appendMod(duplicatePacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    /**
     *   Lose a packet. Selects the packet to be lost
     *
     *   @param  Scanner being used by CLI thread
     *   @return void
     */
    private void losePacket(Scanner sc)
    {
        SimulatePacketInfo losePacket = selectPacket(sc);
        
        losePacket.setModType(TFTPCommon.ModificationType.LOSE);
        
        if (!parentSimulator.appendMod(losePacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    /**
     *   Simulate invalid TID condition. Selects the packet to send on new socket
     *
     *   @param  Scanner being used by CLI thread
     *   @return void
     */
    private void invalidTID(Scanner sc)
    {
        SimulatePacketInfo invalidTID = selectPacket(sc);

        if (invalidTID.getPacketType() == TFTPCommon.PacketType.REQUEST)
        {
            System.out.println("Can't invalid TID on a request, modification cancelled");
            return;
        }

        invalidTID.setModType(TFTPCommon.ModificationType.INVALIDTID);

        if (!parentSimulator.appendMod(invalidTID))
        {
            System.out.println("Failed to add modification!");
        }
    }

    /**
    *   CLI for TFTPIntHost, allows user to set verbosity and exit (non-graceful)
    *
    *   @param  none
    *   @return none
    */
    public void commandLine() 
    {
        Scanner sc = new Scanner(System.in);

        while (cliRunning)
        {
            System.out.println("TFTP Error Simulator");
            System.out.println("--------------------");
            System.out.println("contents: Modify packet contents");
            System.out.println("delay: Delay packet");
            System.out.println("dup: Duplicate packet");
            System.out.println("lose: Lose packet");
            System.out.println("tid: Send packet with invalid TID");
            System.out.println("c: Cancel pending modification");
            System.out.println("i: Set IP address of TFTP Server (current: " + serverAddress + ")");
            System.out.println("p: Print modifications");
            System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");
            
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("contents") )
            {
                modifyContents(sc);
            }
            else if ( scIn.equalsIgnoreCase("delay") )
            {
                delayPacket(sc);              
            }
            else if ( scIn.equalsIgnoreCase("dup") )
            {
                duplicatePacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("lose") )
            {
                losePacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("tid") )
            {
                invalidTID(sc);
            }
            else if ( scIn.equalsIgnoreCase("c") )
            {
                deletePendingMod(sc);
            }
            else if ( scIn.equalsIgnoreCase("i") )
            {
                setServerAddress(sc);
            }
            else if ( scIn.equalsIgnoreCase("p") )
            {
                System.out.println(parentSimulator.simulateListToString());
            }
            else if ( scIn.equalsIgnoreCase("v") ) 
            {
                System.out.println("Enter verbosity (none, some, all): ");
                String strVerbosity = sc.nextLine();

                if ( strVerbosity.equalsIgnoreCase("none") ) 
                {   verbosity = TFTPCommon.Verbosity.NONE;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("some") ) 
                {   verbosity = TFTPCommon.Verbosity.SOME;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("all") )
                {   verbosity = TFTPCommon.Verbosity.ALL;
                    parentSimulator.setVerbosity(verbosity);
                }
                else 
                {
                    System.out.println("Invalid verbosity");
                }
            }
            else if ( scIn.equalsIgnoreCase("q") ) 
            {
                sc.close();
                System.exit(1);
            }
            else if ( scIn.equalsIgnoreCase("") == false ) 
            {
                System.out.println("Invalid option");
            }
        }
    }

    public void run() 
    {
        this.commandLine();
    }
}

/**
 * SimulatePacketInfo contains information of a packet modification, including packet number,
 * type, modification type and modification details
 *
 * @author        Majeed Mirza
 * @author        Cyrus Sadeghi
 */
class SimulatePacketInfo 
{
	private TFTPCommon.PacketType pType;
	private int pNum, delayDuplicateGap, opcode, blockNum, length;
    private String fileName, fileMode;
    private Boolean removeName, removeMode;
	private TFTPCommon.ModificationType modType;
    private List<ModifyByte> modByte;
    private List<TFTPCommon.ContentSubmod> subMod;

    public SimulatePacketInfo (int pNum, TFTPCommon.PacketType pType)
    {
        this.pNum = pNum;
        this.pType = pType;
        this.subMod = new ArrayList<TFTPCommon.ContentSubmod>();
        this.length = -1;
        fileName = new String();
        fileMode = new String();
        modByte = new ArrayList<ModifyByte>();
        removeName = false;
        removeMode = false;
    }

    public TFTPCommon.PacketType getPacketType ()
    {
        return pType;
    }

    public int getPacketNum ()
    {
        return pNum;
    }

    public TFTPCommon.ModificationType getModType ()
    {
        return modType;
    }

    public int getSubModListSize ()
    {
        return subMod.size();
    }

    public boolean subModListHas (TFTPCommon.ContentSubmod cs)
    {
        return subMod.contains(cs);
    }

    public int getDelayDuplicateGap ()
    {
        return delayDuplicateGap;
    }

    public List<ModifyByte> getModByteList ()
    {
        return modByte;
    }

    public String getFileName ()
    {
        return fileName;
    }

    public String getFileMode ()
    {
        return fileMode;
    }

    public Boolean getRmName ()
    {
        return removeName;
    }

    public Boolean getRmMode ()
    {
        return removeMode;
    }

    public int getLength ()
    {
        return length;
    }

    public void setModType (TFTPCommon.ModificationType modType)
    {
        this.modType = modType;
    }

    public void addContentModType (TFTPCommon.ContentSubmod subMod)
    {
        setModType(TFTPCommon.ModificationType.CONTENTS);
        this.subMod.add(subMod);
    }

    public void setDelayDuplicateGap (int delayDuplicateGap)
    {
        this.delayDuplicateGap = delayDuplicateGap;
    }

    public void setOpcode (int opcode)
    {
        this.opcode = opcode;
        modByte.add(new ModifyByte(0, (byte) (opcode / 10)));
        modByte.add(new ModifyByte(1, (byte) (opcode % 10)));
    }

    public void setFileName (String fileName)
    {
        this.fileName = fileName;
    }

    public void setFileMode (String fileMode)
    {
        this.fileMode = fileMode;
    }

    public void removeName ()
    {
        removeName = true;
    }

    public void removeMode ()
    {
        removeMode = true;
    }

    public void setBlockNum (int blockNum)
    {
        this.blockNum = blockNum;
        modByte.add(new ModifyByte(2, (byte) (blockNum / 256)));
        modByte.add(new ModifyByte(3, (byte) (blockNum % 256)));
    }

    public void addModByte (ModifyByte modByte)
    {
        this.modByte.add(modByte);
    }

    public void setLength (int length)
    {
        this.length = length;
    }

    public String toString () 
    {
        String returnString = new String ("Packet: " + TFTPCommon.packetTypeToString(pType) + " " + pNum + " Error Type: " + TFTPCommon.errorSimulateToString(modType));
        
        if (getModType() == TFTPCommon.ModificationType.DELAY)
        {
            returnString = returnString + " Delay Amount: " + getDelayDuplicateGap() + " ms";
        }

        else if (getModType() == TFTPCommon.ModificationType.DUPLICATE)
        {
            returnString = returnString + " Duplicate Gap: " + getDelayDuplicateGap() + " ms";
        }

        else if (getModType() == TFTPCommon.ModificationType.CONTENTS)
        {
            for (TFTPCommon.ContentSubmod cs : subMod)
            {
                if (cs == TFTPCommon.ContentSubmod.FILENAME)
                {
                    if (!removeName)
                    {
                        returnString = returnString + System.lineSeparator() + "Change filename to \"" + fileName + "\"";
                    }
                    else
                    {
                        returnString = returnString + System.lineSeparator() + "Remove filename";
                    }
                }

                if (cs == TFTPCommon.ContentSubmod.FILEMODE)
                {
                    if (!removeMode)
                    {
                        returnString = returnString + System.lineSeparator() + "Change filemode to \"" + fileMode + "\"";
                    }
                    else
                    {
                        returnString = returnString + System.lineSeparator() + "Remove filemode";
                    }
                }

                if (cs == TFTPCommon.ContentSubmod.LENGTH)
                {
                    returnString = returnString + System.lineSeparator() + "Change length to " + length;
                }
            }

            for (ModifyByte m : modByte)
            {
                returnString = returnString + System.lineSeparator() + "Packet Contents: " + m.toString();
            }
        }

        return returnString;
    }
}


/**
 * ModifyByte contains information for a byte modification in a packet. It contains
 * the byte to modify and the new value for the byte
 *
 * @author        Cyrus Sadeghi
 */
class ModifyByte
{
    private Integer position;
    private byte value;

    public ModifyByte (Integer position, byte value)
    {
        this.position = position;
        this.value = value;
    }

    public Integer getPosition ()
    {
        return position;
    }

    public byte getValue ()
    {
        return value;
    }

    public String toString ()
    {
       return (new String ("Change value of byte " + position + " to " + Byte.toUnsignedInt(value)) + " (unsigned int value)");
    }
}
