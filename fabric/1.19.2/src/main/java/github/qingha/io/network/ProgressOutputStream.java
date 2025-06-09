package github.qingha.io.network;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class ProgressOutputStream extends OutputStream {
    public interface WriteListener {
        void registerWrite(long amountOfBytesWritten) throws IOException;
    }

    private final OutputStream outstream;
    private long bytesWritten = 0;
    private final WriteListener writeListener;

    public ProgressOutputStream(OutputStream outstream, WriteListener writeListener) {
        this.outstream = outstream;
        this.writeListener = writeListener;
    }

    @Override
    public void write(int b) throws IOException {
        outstream.write(b);
        bytesWritten++;
        writeListener.registerWrite(bytesWritten);
    }

    @Override
    public void write(byte @NotNull [] b) throws IOException {
        outstream.write(b);
        bytesWritten += b.length;
        writeListener.registerWrite(bytesWritten);
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
        outstream.write(b, off, len);
        bytesWritten += len;
        writeListener.registerWrite(bytesWritten);
    }

    @Override
    public void flush() throws IOException {
        outstream.flush();
    }

    @Override
    public void close() throws IOException {
        outstream.close();
    }
}