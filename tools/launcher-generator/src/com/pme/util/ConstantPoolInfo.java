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

import java.io.DataInputStream;
import java.io.IOException;

public class ConstantPoolInfo {
  private int myType = -1;
  private short myIindex1 = -1;
  private short myIndex2 = -1;
  private String myStrValue;
  private int myIntValue;
  private long myLongValue;
  private float myFloatValue;
  private double myDoubleValue;

  public static final int CLASS = 7;
  public static final int FIELDREF = 9;
  public static final int METHODREF = 10;
  public static final int STRING = 8;
  public static final int INTEGER = 3;
  public static final int FLOAT = 4;
  public static final int LONG = 5;
  public static final int DOUBLE = 6;
  public static final int INTERFACE = 11;
  public static final int NAMEANDTYPE = 12;
  public static final int ASCIZ = 1;
  public static final int UNICODE = 2;

  public String getStrValue() {
    return myStrValue;
  }

  public int getType() {
    return myType;
  }

  public void read(DataInputStream stream)throws IOException {
    myType = stream.readByte();
    switch (myType) {
      case CLASS:
        myIindex1 = stream.readShort();
        break;
      case FIELDREF:
        myIindex1 = stream.readShort();
        myIndex2 = stream.readShort();
        break;
      case METHODREF:
        myIindex1 = stream.readShort();
        myIndex2 = stream.readShort();
        break;
      case INTERFACE:
        myIindex1 = stream.readShort();
        myIndex2 = stream.readShort();
        break;
      case NAMEANDTYPE:
        myIindex1 = stream.readShort();
        myIndex2 = stream.readShort();
        break;
      case STRING:
        myIindex1 = stream.readShort();
        break;
      case INTEGER:
        myIntValue = stream.readInt();
        break;
      case FLOAT:
        myFloatValue = stream.readFloat();
        break;
      case LONG:
        myLongValue = stream.readLong();
        break;
      case DOUBLE:
        myDoubleValue = stream.readDouble();
        break;
      case ASCIZ:
      case UNICODE:
        StringBuffer buff = new StringBuffer();
        int len = stream.readShort();
        while (len > 0) {
          char c = (char) (stream.readByte());
          buff.append(c);
          len--;
        }
        myStrValue = buff.toString();
        break;
    }
  }

}
