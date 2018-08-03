// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.util;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
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
