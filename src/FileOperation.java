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

    public int getNumTFTPBlocks() 
    {
        return (int) Math.ceil(file.length()/ numBytes);
    }

    public int size()
    {
        return (int) file.length();
    }  

    public int readNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        int amountRead = 0;

        try {
            //Skip to next set of data
            inStream.skip(readWriteOffset);

            //Read returns -1 when EOF is reached
            if (-1 != inStream.read(data, dataOffset, numBytes) )
            {
                readWriteOffset += numBytes;
                amountRead = numBytes;
            }
            else
            {
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

    public void writeNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        try {
            outStream.write(data, dataOffset, data.length);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        readWriteOffset += numBytes;
    }

    public FileOperation(String absolutePath, Boolean writeRequest, int bytesRW) 
    {
        readWriteOffset = 0;
        numBytes = bytesRW;
        file = new File(absolutePath);

        //Read requests write to local machine, write requests read from local machine
        if ( writeRequest == false ) 
        {
            try {
                outStream = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                System.out.println("File does not exist!");
                System.exit(1); //todo - can't do this...
            }
        }
        else
        {
            try {
                inStream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                System.out.println("File path does not exist!");
                System.exit(1); //todo - can't do this...
            }
        }
    }
}
