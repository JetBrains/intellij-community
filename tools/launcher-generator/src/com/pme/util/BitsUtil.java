/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2022 JetBrains s.r.o.
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

package com.pme.util;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: Mar 30, 2006
 * Time: 7:10:10 PM
 */
public final class BitsUtil {
  public static String intToHexString( long value ){
    return String.format("0x%08x", (int)value);
  }
  public static String shortToHexString( int value ){
    return String.format("0x%04x", (short)value);
  }
  public static String byteToHexString( int value ){
    return String.format("0x%02x", (byte)value);
  }

  public static char readChar(DataInput stream) throws IOException {
    int b1 = stream.readByte();
    int b2 = stream.readByte();
    return (char) (b1 & 0xFF | ((b2 & 0xFF) << 8));
  }
}
