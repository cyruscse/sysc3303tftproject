package grouptwo;

import java.io.*;

public class FileOperation
{
    private File file;
    private long readWriteOffset;
    private FileInputStream inStream;
    private FileOutputStream outStream;

    public String getFilePath() 
    {
        return file.getAbsolutePath();
    }

    public int getNumTFTPBlocks() 
    {
        //does this work if file is exactly (multiple of) 512 bytes?
        return (int) Math.ceil(file.length()/ 512);
    }

    public int size()
    {
        return (int) file.length();
    }  
    //get rid of hardcoded 512, pass into constructor
    public int readNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        int amountRead = 0;

        try {
            inStream.skip(readWriteOffset);

            if (-1 != inStream.read(data, dataOffset, 512) )
            {
                readWriteOffset += 512;
                amountRead = 512;
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

 /*   public void writeNextDataPacket(byte[] data) throws FileNotFoundException 
    {
        try {
            outStream.write(data, readWriteOffset, data.length);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        readWriteOffset += 512;
    }
*/
    public FileOperation(String absolutePath, Boolean writeRequest) 
    {
        readWriteOffset = 0;
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
                System.out.println("File does not exist!");
                System.exit(1); //todo - can't do this...
            }
        }
    }
}
