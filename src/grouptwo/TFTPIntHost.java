// TFTPSim.java
// This class is the beginnings of an error simulator for a simple TFTP server 
// based on UDP/IP. The simulator receives a read or write packet from a client and
// passes it on to the server.  Upon receiving a response, it passes it on to the 
// client.
// One socket (23) is used to receive from the client, and another to send/receive
// from the server.  A new socket is used for each communication back to the client.   
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;

public class TFTPIntHost 
{
    public static enum Verbosity { NONE, SOME, ALL };
   
    private DatagramSocket receiveSocket;
    private DatagramPacket receivePacket;
    private Verbosity verbosity;
    private TFTPIntHostCommandLine cliThread;
    private byte [] data;

    public TFTPIntHost()
    {
        try {
            receiveSocket = new DatagramSocket(23);
        } catch (SocketException se) {
            se.printStackTrace();
            System.exit(1);
        }

        verbosity = Verbosity.NONE;
        cliThread = new TFTPIntHostCommandLine(this);
    }

    private void processClients()
    {
        cliThread.start();

        System.out.println("Error Simulator: Waiting for clients.");
        while (true)
        {
            data = new byte[100];
            receivePacket = new DatagramPacket(data, data.length);

            try {
                receiveSocket.receive(receivePacket); //TODO: need to print packet details
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            Thread client = new Thread(new ErrorSimulator(receivePacket, verbosity));
            client.start();
        }
    }

    /**
    *   Called by CLI thread, sets the verbosity for new ClientConnectionThreads
    *   Note: This doesn't change verbosity for ongoing transfers
    *
    *   @param  Verbosity verbosity to set to
    *   @return none
    */
    public void setVerbosity(Verbosity v) 
    {
        this.verbosity = v;
    }

    public static void main( String args[] )
    {
        TFTPIntHost s = new TFTPIntHost();
        s.processClients();
    }
}

class ErrorSimulator extends Thread
{
    private DatagramSocket sendReceiveSocket;
    private DatagramPacket sendPacket, receivePacket;
    private TFTPIntHost.Verbosity verbosity;
    private InetAddress clientAddress;
    private int clientPort, serverPort, len;
    private byte [] data;

    public ErrorSimulator(DatagramPacket firstPacket, TFTPIntHost.Verbosity verbose)
    {
        clientAddress = firstPacket.getAddress();
        clientPort = firstPacket.getPort();
        len = firstPacket.getLength();
        data = firstPacket.getData();
        verbosity = verbose;
        
        try {
            sendReceiveSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void passOnTFTP()
    {
        int j = 0;

        //TODO might have to move request code to loop (?)
        sendPacket = new DatagramPacket(data, len, clientAddress, 69); //TODO define 69 somewhere

        len = sendPacket.getLength();
        
        if (verbosity != TFTPIntHost.Verbosity.NONE)
        {
            System.out.println("Simulator: sending request packet.");
            System.out.println("To host: " + sendPacket.getAddress());
            System.out.println("Destination host port: " + sendPacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == TFTPIntHost.Verbosity.ALL)
            {
                System.out.println("Containing: ");
                for (j = 0; j < len; j++) 
                {
                    System.out.println("byte " + j + " " + data[j]);
                }
            }
        }

        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }      
        
        while (true) 
        {
            // receive from server
            data = new byte[516];
            receivePacket = new DatagramPacket(data, data.length);

            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: Waiting for packet from server.");
            }

            try {
                sendReceiveSocket.receive(receivePacket);
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            serverPort = receivePacket.getPort();
            len = receivePacket.getLength();

            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: Packet received from server:");
                System.out.println("From host: " + receivePacket.getAddress());
                System.out.println("Host port: " + receivePacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPIntHost.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }
            
            // send to client
            sendPacket = new DatagramPacket(data, receivePacket.getLength(),
                               receivePacket.getAddress(), clientPort);

            len = sendPacket.getLength();
            
            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: Sending packet to client:");
                System.out.println("To host: " + sendPacket.getAddress());
                System.out.println("Destination host port: " + sendPacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPIntHost.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                       System.out.println("byte " + j + " " + data[j]);
                    }   
                }
            }   

            try {
                sendReceiveSocket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: packet sent using port " + sendReceiveSocket.getLocalPort());
                System.out.println();
            }

            // receive from client
            data = new byte[516];
            receivePacket = new DatagramPacket(data, data.length);

            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: Waiting for packet from client.");
            }

            try {
                // Block until a datagram is received via sendReceiveSocket.
                sendReceiveSocket.receive(receivePacket);
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            len = receivePacket.getLength();
            // Process the received datagram.
            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: Packet received from client:");
                System.out.println("From host: " + receivePacket.getAddress());
                System.out.println("Host port: " + receivePacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPIntHost.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }
         
            sendPacket = new DatagramPacket(data, len, receivePacket.getAddress(), serverPort);
        
            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("Simulator: sending packet to server.");
            }

            len = sendPacket.getLength();
            if (verbosity != TFTPIntHost.Verbosity.NONE)
            {
                System.out.println("To host: " + sendPacket.getAddress());
                System.out.println("Destination host port: " + sendPacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPIntHost.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }

            // Send to server.
            try {
                sendReceiveSocket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
         
        } // end of loop
    }

    public void run()
    {
        this.passOnTFTP();
    }
}

class TFTPIntHostCommandLine extends Thread
{
    private TFTPIntHost.Verbosity verbosity;
    private TFTPIntHost parentSimulator;
    private Boolean cliRunning;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPIntHost.Verbosity.NONE;
        cliRunning = true;
    }

    /**
    *   Converts Verbosity to String
    *
    *   @param  Verbosity to convert to String
    *   @return String of converted Verbosity
    */
    public static String verbosityToString(TFTPIntHost.Verbosity ver)
    {
        if ( ver == TFTPIntHost.Verbosity.NONE )
        {
            return "normal";
        }
        else if ( ver == TFTPIntHost.Verbosity.SOME )
        {
            return "basic packet details";
        }
        else if ( ver == TFTPIntHost.Verbosity.ALL )
        {
            return "full packet details (including data contents)";
        }
        return "";
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
        String scIn = new String();

        while (cliRunning)
        {
            System.out.println("TFTP Error Simulator");
            System.out.println("v: Set verbosity (current: " + verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");
            
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("v") ) 
            {
                System.out.println("Enter verbosity (none, some, all): ");
                String strVerbosity = sc.nextLine();

                if ( strVerbosity.equalsIgnoreCase("none") ) 
                {   verbosity = TFTPIntHost.Verbosity.NONE;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("some") ) 
                {   verbosity = TFTPIntHost.Verbosity.SOME;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("all") )
                {   verbosity = TFTPIntHost.Verbosity.ALL;
                    parentSimulator.setVerbosity(verbosity);
                }
                else 
                {
                    System.out.println("Invalid verbosity");
                }
            }

            else if ( scIn.equalsIgnoreCase("q") ) 
            {
                System.exit(1);
            }
        }
    }

    public void run() 
    {
        this.commandLine();
    }
}


