/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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
 * Date: Mar 30, 2006
 * Time: 7:10:10 PM
 */
public class BitsUtil {
  public static int revertBytesOfShort( short shortValue ){
    return ((shortValue << 8) & 0xff00) + ((shortValue >> 8) & 0xff);
  }

  public static long revertBytesOfInt( int intValue ){
    long result = (intValue & 0x000000ff);
    result <<= 24;
    result += ((intValue & 0x0000ff00) << 8) + ((intValue & 0x00ff0000) >> 8) + ((intValue >> 24) & 0xff);
    return result;
  }

  public static long revertBytesOfLong(long longValue) {
    long ms = revertBytesOfInt((int) (longValue >> 32));
    long ls = revertBytesOfInt((int) longValue);
    return ms | ls << 32;
  }

  public static int unsignedByte( byte byteValue ){
    int result = byteValue;
    return (result & 0xff);
  }
  private static String toHexString( long value, int size ){
    String strValue = Long.toHexString( value );
    if ( strValue.length() > size ){
      strValue = strValue.substring( strValue.length() - size );
    }
    StringBuffer buffer = new StringBuffer( strValue.length() + 1 + size );
    buffer.append( "0x" );
    int dif = size - strValue.length();
    for ( int i = 0; i < dif; ++i ){
      buffer.append( "0" );
    }
    buffer.append( strValue );
    return buffer.toString();
  }

  public static String intToHexString( long value ){
    return toHexString( value, 8 );
  }
  public static String shortToHexString( int value ){
    return toHexString( value, 4 );
  }
  public static String byteToHexString( int value ){
    return toHexString( value, 2 );
  }

  public static char readChar(DataInput stream) throws IOException {
    int b1 = stream.readByte();
    int b2 = stream.readByte();
    return (char) (b1 + (b2 << 8));

  }
}
