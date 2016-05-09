// FileOperation.java
// This class provides file IO for the client and server classes
// It can split a file into chunks to be used for data packets
// It also stitches the chunks back into a file to write to disk
package grouptwo;

import java.io.*;

public class FileOperation
{
    private File file;
    private long readWriteOffset;
    private FileInputStream inStream;
    private FileOutputStream outStream;
    private int numBytes;

    public String getFilePath() 
    {
        return file.getAbsolutePath();
    }

    //Gets number of TFTP data packets needed to transfer file
    public int getNumTFTPBlocks() 
    {   
        if ( file.length() == 0 )
        {
            return 1;
        }
        return (int) Math.ceil(file.length() / numBytes);
    }

    public int size()
    {
        return (int) file.length();
    }  

    //Read FileInputStream in chunks then write each chunk into the provided byte array
    public int readNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        int amountRead = 0;

        try {
            //Skip to next set of data (up to what was read on last method call)
            inStream.skip(readWriteOffset);

            //Read numBytes from where we skipped to, then adjust the amount to skip
            //on next method call
            if (-1 != (amountRead = inStream.read(data, dataOffset, numBytes)) )
            {
                readWriteOffset += numBytes;
                amountRead = numBytes;
            }
            else
            {
                //If read didn't return -1 we read less than numBytes, so
                //we have to find out how much we read
                for (amountRead = 4; amountRead < data.length; amountRead++)
                {
                    if ( data[amountRead] == 0 )
                    {
                        return (amountRead + 1);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return amountRead;
    }

    //Write each chunk of data into the FileOutputStream in order to recreate file
    public void writeNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        try {
            outStream.write(data, dataOffset, numBytes);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        readWriteOffset += numBytes;
    }

    public FileOperation(String absolutePath, Boolean localRead, int bytesRW) throws FileNotFoundException
    {
        readWriteOffset = 0;
        numBytes = bytesRW;
        file = new File(absolutePath);

        //Client: Read Request writes to local machine
        //Server: Write Request writes to local machine
        if ( localRead == false ) 
        {
            outStream = new FileOutputStream(absolutePath);
        }
        //Client: Write Request reads from local machine
        //Server: Read Request reads from local machine
        else
        {
            inStream = new FileInputStream(absolutePath);
        }
    }
}
