/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class IOUtil {
  public static String readString(DataInput stream) throws IOException {
    int length = stream.readInt();
    if (length == -1) return null;

    char[] chars = new char[length];
    byte[] bytes = new byte[length*2];
    stream.readFully(bytes);

    for (int i = 0, i2 = 0; i < length; i++, i2+=2) {
      chars[i] = (char)((bytes[i2] << 8) + bytes[i2 + 1]);
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
}
