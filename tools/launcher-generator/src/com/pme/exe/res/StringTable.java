// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 12:43:28 PM
 */
public class StringTable {
    String[] strings = new String[16];

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
      for ( int i = 0; i < strings.length; ++i){
        size += 2 + strings[i].length() * 2;
      }
      ByteArrayOutputStream bytesStream = new ByteArrayOutputStream(size);
      DataOutputStream stream = new DataOutputStream(bytesStream);
      for ( int i = 0; i < strings.length; ++i){
        int count = strings[i].length();
        new Bin.Word().setValue( count ).write( stream );
        if ( count != 0 ){
          new Bin.Txt( "", strings[i] ).write( stream );
        }
      }
      return bytesStream.toByteArray();
    }
  }
