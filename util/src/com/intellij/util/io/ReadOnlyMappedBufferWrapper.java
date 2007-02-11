/*
 * @author max
 */
package com.intellij.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ReadOnlyMappedBufferWrapper extends MappedBufferWrapper {
  public ReadOnlyMappedBufferWrapper(final File file, final int pos) {
    super(file, pos, file.length() - pos);
  }

  public MappedByteBuffer map() {
    try {
      FileInputStream stream = new FileInputStream(myFile);
      FileChannel channel = stream.getChannel();
      try {
        return channel.map(FileChannel.MapMode.READ_ONLY, myPosition, myLength);
      }
      finally {
        channel.close();
        stream.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Mapping failed for: " + myFile.getPath(), e);
    }
  }
}
