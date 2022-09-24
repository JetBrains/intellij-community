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

import com.pme.exe.Bin;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 12:43:28 PM
 */
public class StringTable {
    final String[] strings = new String[16];

    public StringTable(byte[] bytes) throws IOException {
      ByteArrayInputStream bytesStream = new ByteArrayInputStream(bytes);
      DataInputStream stream = new DataInputStream(bytesStream);
      Bin.Word count = new Bin.Word();
      for (int i = 0; i < 16; ++i) {
        count.read(stream);
        if ( count.getValue() != 0 ){
          Bin.Txt txt = new Bin.Txt("", (int)(count.getValue() * 2));
          txt.read( stream );
          strings[i] = txt.getText();
        } else {
          strings[i] = "";
        }
      }
    }

    public void setString( int index, String string ){
      strings[index] = string;
    }

    public byte[] getBytes() throws IOException {
      int size = 0;
      for (String string : strings) {
        size += 2 + string.length() * 2;
      }
      ByteArrayOutputStream bytesStream = new ByteArrayOutputStream(size);
      DataOutputStream stream = new DataOutputStream(bytesStream);
      for (String string : strings) {
        int count = string.length();
        new Bin.Word().setValue(count).write(stream);
        if (count != 0) {
          new Bin.Txt("", string).write(stream);
        }
      }
      return bytesStream.toByteArray();
    }
  }
