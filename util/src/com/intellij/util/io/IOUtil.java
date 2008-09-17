/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;

public class IOUtil {
  private static final int STRING_HEADER_SIZE = 1;
  private static final int STRING_LENGTH_THRESHOLD = 255;

  @NonNls private static final String LONGER_THAN_64K_MARKER = "LONGER_THAN_64K";

  private IOUtil() {}

  public static String readString(DataInput stream) throws IOException {
    int length = stream.readInt();
    if (length == -1) return null;

    char[] chars = new char[length];
    byte[] bytes = new byte[length*2];
    stream.readFully(bytes);

    for (int i = 0, i2 = 0; i < length; i++, i2+=2) {
      chars[i] = (char)((bytes[i2] << 8) + (bytes[i2 + 1] & 0xFF));
    }

    return new String(chars);
  }

  public static void writeString(String s, DataOutput stream) throws IOException {
    if (s == null) {
      stream.writeInt(-1);
      return;
    }
    char[] chars = s.toCharArray();
    byte[] bytes = new byte[chars.length * 2];

    stream.writeInt(chars.length);
    for (int i = 0, i2 = 0; i < chars.length; i++, i2 += 2) {
      char aChar = chars[i];
      bytes[i2] = (byte)((aChar >>> 8) & 0xFF);
      bytes[i2 + 1] = (byte)((aChar) & 0xFF);
    }

    stream.write(bytes);
  }

  public static void writeUTFTruncated(final DataOutput stream, final String text) throws IOException {
    if (text.length() > 65535) {
      stream.writeUTF(text.substring(0, 65535));
    }
    else {
      stream.writeUTF(text);
    }
  }

  public static byte[] allocReadWriteUTFBuffer() {
    return new byte[STRING_LENGTH_THRESHOLD + STRING_HEADER_SIZE];
  }

  public static void writeUTFFast(final byte[] buffer, final DataOutput storage, final String value) throws IOException {
    int len = value.length();
    if (len < STRING_LENGTH_THRESHOLD && isAscii(value)) {
      buffer[0] = (byte)len;
      for (int i = 0; i < len; i++) {
        buffer[i + STRING_HEADER_SIZE] = (byte)value.charAt(i);
      }
      storage.write(buffer, 0, len + STRING_HEADER_SIZE);
    }
    else {
      storage.writeByte((byte)0xFF);

      try {
        storage.writeUTF(value);
      }
      catch (UTFDataFormatException e) {
        storage.writeUTF(LONGER_THAN_64K_MARKER);
        writeString(value, storage);
      }
    }
  }

  public static String readUTFFast(final byte[] buffer, final DataInput storage) throws IOException {
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      String result = storage.readUTF();
      if (LONGER_THAN_64K_MARKER.equals(result)) {
        return readString(storage);
      }

      return result;
    }

    final char[] chars = new char[len];
    storage.readFully(buffer, 0, len);
    for (int i = 0; i < len; i++) {
      chars[i] = (char)buffer[i];
    }

    return new String(chars);
  }

  static boolean isAscii(final String str) {
    for (int i = 0; i != str.length(); ++ i) {
      final char c = str.charAt(i);
      if (c < 0 || c >= 128) {
        return false;
      }
    }
    return true;
  }
}
