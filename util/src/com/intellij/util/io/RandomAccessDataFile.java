/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.util.TimedComputable;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class RandomAccessDataFile {
  private final static PagePool ourPool = new PagePool();
  private static int ourFilesCount = 0;
  private final int myCount = ourFilesCount++;

  private final byte[] myTypedIOBuffer = new byte[8];

  private final TimedComputable<FileChannel> myChannel;
  private long mySize;

  public RandomAccessDataFile(final File file) throws IOException {
    myChannel = new TimedComputable<FileChannel>(null) {
      @NotNull
      protected FileChannel calc() {
        try {
          RandomAccessFile raf = new RandomAccessFile(file, "rw");
          return raf.getChannel();
        }
        catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }

      public synchronized void dispose() {
        FileChannel channel = myT;
        super.dispose();
        try {
          channel.close();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    final FileChannel channel = myChannel.acquire();
    mySize = channel.size();
    myChannel.release();
  }

  public void put(long addr, byte[] bytes, int off, int len) {
    mySize = Math.max(mySize, addr + len);

    if (len > Page.PAGE_SIZE) {
      try {
        ourPool.flushPagesInRange(this, addr, len);
        final FileChannel channel = myChannel.acquire();
        channel.write(ByteBuffer.wrap(bytes, off, len), addr);
        myChannel.release();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      while (len > 0) {
        final Page page = ourPool.alloc(this, addr);
        int written = page.put(addr, bytes, off, len);
        len -= written;
        addr += written;
        off += written;
        ourPool.reclaim(page);
      }
    }
  }

  public void get(long addr, byte[] bytes, int off, int len) {
    if (len > Page.PAGE_SIZE) {
      try {
        ourPool.flushPagesInRange(this, addr, len);

        final FileChannel channel = myChannel.acquire();
        channel.read(ByteBuffer.wrap(bytes, off, len), addr);
        myChannel.release();

        mySize = Math.max(mySize, addr + len);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      while (len > 0) {
        final Page page = ourPool.alloc(this, addr);
        int read = page.get(addr, bytes, off, len);
        len -= read;
        addr += read;
        off += read;
        ourPool.reclaim(page);
      }
    }
  }

  public void putInt(long addr, int value) {
    byte[] buffer = myTypedIOBuffer;
    buffer[0] = (byte)((value >>> 24) & 0xFF);
    buffer[1] = (byte)((value >>> 16) & 0xFF);
    buffer[2] = (byte)((value >>> 8) & 0xFF);
    buffer[3] = (byte)(value & 0xFF);

    put(addr, buffer, 0, 4);
  }

  public int getInt(long addr) {
    byte[] buffer = myTypedIOBuffer;
    get(addr, buffer, 0, 4);

    int ch1 = buffer[0] & 0xff;
    int ch2 = buffer[1] & 0xff;
    int ch3 = buffer[2] & 0xff;
    int ch4 = buffer[3] & 0xff;
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
  }

  public void putLong(long addr, long value) {
    byte[] buffer = myTypedIOBuffer;
    buffer[0] = (byte)((value >>> 56) & 0xFF);
    buffer[1] = (byte)((value >>> 48) & 0xFF);
    buffer[2] = (byte)((value >>> 40) & 0xFF);
    buffer[3] = (byte)((value >>> 32) & 0xFF);
    buffer[4] = (byte)((value >>> 24) & 0xFF);
    buffer[5] = (byte)((value >>> 16) & 0xFF);
    buffer[6] = (byte)((value >>> 8) & 0xFF);
    buffer[7] = (byte)(value & 0xFF);

    put(addr, buffer, 0, 8);
  }

  public void putByte(final long addr, final byte b) {
    myTypedIOBuffer[0] = b;
    put(addr, myTypedIOBuffer, 0, 1);
  }

  public byte getByte(long addr) {
    get(addr, myTypedIOBuffer, 0, 1);
    return myTypedIOBuffer[0];
  }

  public String getUTF(long addr) {
    try {
      int len = getInt(addr);
      byte[] bytes = new byte[ len ];
      get(addr + 4, bytes, 0, len);
      return new String(bytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
      return "";
    }
  }

  public void putUTF(long addr, String value) {
    try {
      final byte[] bytes = value.getBytes("UTF-8");
      putInt(addr, bytes.length);
      put(addr + 4, bytes, 0, bytes.length);
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
    }
  }

  public long getLong(long addr) {
    byte[] buffer = myTypedIOBuffer;
    get(addr, buffer, 0, 8);

    long ch1 = buffer[0] & 0xff;
    long ch2 = buffer[1] & 0xff;
    long ch3 = buffer[2] & 0xff;
    long ch4 = buffer[3] & 0xff;
    long ch5 = buffer[4] & 0xff;
    long ch6 = buffer[5] & 0xff;
    long ch7 = buffer[6] & 0xff;
    long ch8 = buffer[7] & 0xff;

    return ((ch1 << 56) + (ch2 << 48) + (ch3 << 40) + (ch4 << 32) + (ch5 << 24) + (ch6 << 16) + (ch7 << 8) + ch8);
  }

  public long length() {
    return mySize;
  }

  public void dispose() {
    ourPool.flushPages(this);
    myChannel.dispose();
  }

  public void loadPage(final Page page) {
    try {
      page.getBuf().position(0);
      final FileChannel channel = myChannel.acquire();
      channel.read(page.getBuf(), page.getOffset());
      myChannel.release();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flushPage(final Page page) {
    try {
      flush(page.getBuf(), page.getOffset(), Page.PAGE_SIZE);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void flush(final ByteBuffer buf, final long offset, final int length) throws IOException {
    final ByteBuffer flush;
    if (offset + length > mySize) {
      flush = ByteBuffer.wrap(buf.array(), 0, (int)(mySize - offset));
    }
    else {
      flush = buf;
      flush.position(0);
    }

    final FileChannel channel = myChannel.acquire();
    channel.write(flush, offset);
    myChannel.release();
  }

  public int hashCode() {
    return myCount;
  }
}