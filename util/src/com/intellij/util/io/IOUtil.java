/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
}
