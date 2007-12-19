package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 18, 2007
*/
public class EnumeratorStringDescriptor implements PersistentEnumerator.DataDescriptor<String> {
  private static final int STRING_HEADER_SIZE = 1;
  private static final int STRING_LENGTH_THRESHOLD = 255;
  private final byte[] buffer = new byte[STRING_HEADER_SIZE + STRING_LENGTH_THRESHOLD];

  public int getHashCode(final String value) {
    return value.hashCode();
  }

  public boolean isEqual(final String val1, final String val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput storage, final String value) throws IOException {
    int len = value.length();
    if (len < STRING_LENGTH_THRESHOLD && isAscii(value)) {
      final byte[] buf = buffer;
      buf[0] = (byte)len;
      for (int i = 0; i < len; i++) {
        buf[i + STRING_HEADER_SIZE] = (byte)value.charAt(i);
      }
      storage.write(buf, 0, len + STRING_HEADER_SIZE);
    }
    else {
      storage.writeByte((byte)0xFF);
      storage.writeUTF(value);
    }
  }

  public String read(final DataInput storage) throws IOException {
    final int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      return storage.readUTF();
    }

    final char[] chars = new char[len];
    final byte[] buf = buffer;
    storage.readFully(buf, 0, len);
    for (int i = 0; i < len; i++) {
      chars[i] = (char)buf[i];
    }
    return new String(chars);
  }
  
  private static boolean isAscii(final String str) {
    for (int i = 0; i != str.length(); ++ i) {
      final char c = str.charAt(i);
      if (c < 0 || c >= 128) {
        return false;
      }
    }
    return true;
  }
  
}
