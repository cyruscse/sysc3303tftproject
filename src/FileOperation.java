package grouptwo;

import java.io.*;

public class FileOperation
{
    private File file;
    private int  readWriteOffset;
    private FileInputStream inStream;
    private FileOutputStream outStream;

    public String getFilePath() {
        return file.getAbsolutePath();
    }

    public byte[] readNextDataPacket() throws FileNotFoundException, IOException {
        inStream = new FileInputStream(file);
        byte[] data = new byte[512];

        if ( 512 == inStream.read(data, readWriteOffset, data.length) ) {
            readWriteOffset += 512;
        }

        return data;
    }

    public void writeNextDataPacket(byte[] data) throws FileNotFoundException, SecurityException {
        outStream = new FileOutputStream(file);

        outStream.write(data, readWriteOffset, data.length);
    }

    public FileOperation(String absolutePath) {
        readOffset = 0;
        file = new File(absolutePath);
    }
}
