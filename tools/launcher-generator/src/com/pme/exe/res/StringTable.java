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

package com.pme.exe.res;

import com.pme.exe.Bin.WCharStringSP;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 12:43:28 PM
 */
public class StringTable {
    final WCharStringSP[] strings = new WCharStringSP[16];

    public StringTable(byte[] bytes) throws IOException {
      ByteArrayInputStream bytesStream = new ByteArrayInputStream(bytes);
      DataInputStream stream = new DataInputStream(bytesStream);
      for (int i = 0; i < 16; ++i) {
        strings[i] = new WCharStringSP();
        strings[i].read(stream);
      }
    }

    public void setString( int index, String string ){
      strings[index].setValue(string);
    }

    public byte[] getBytes() throws IOException {
      int size = 0;
      for (WCharStringSP string : strings) {
        size += string.sizeInBytes();
      }
      ByteArrayOutputStream bytesStream = new ByteArrayOutputStream(size);
      DataOutputStream stream = new DataOutputStream(bytesStream);
      for (WCharStringSP string : strings) {
        string.write(stream);
      }
      return bytesStream.toByteArray();
    }
  }
