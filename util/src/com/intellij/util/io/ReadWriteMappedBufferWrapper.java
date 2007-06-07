/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ReadWriteMappedBufferWrapper extends MappedBufferWrapper {
  @NonNls private static final String RW = "rw";

  public ReadWriteMappedBufferWrapper(final File file) {
    super(file, 0, file.length());
  }

  public ReadWriteMappedBufferWrapper(final File file, int offset, int len) {
    super(file, offset, len);
  }

  public MappedByteBuffer map() {
    MappedByteBuffer buf;
    try {
      RandomAccessFile raf = new RandomAccessFile(myFile, RW);
      final FileChannel channel = raf.getChannel();
      buf = null;
      try {
        buf = channel.map(FileChannel.MapMode.READ_WRITE, myPosition, myLength);
      }
      catch (IOException e) {
        throw new RuntimeException("Mapping failed: " + myFile.getAbsolutePath(), e);
      }
      finally {
        channel.close();
        raf.close();
      }
    }
    catch (IOException e) {
      buf = null;
    }

    if (buf == null) {
      throw new RuntimeException("Mapping failed: " + myFile.getAbsolutePath());
    }

    return buf;
  }
}