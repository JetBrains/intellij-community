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

package com.pme.exe.res.vi;

import com.pme.exe.Bin;

import java.io.*;
import java.util.ArrayList;

/**
 * Date: May 10, 2006
 * Time: 8:26:03 PM
 */
public class StringTableEntry extends Bin.Structure {
  public StringTableEntry(String key) {
    super(key);
    addMember(new Word("wLength"));
    addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    addMember(new Bytes("key", key.length() * 2));
  }

  public void readWithPadding(DataInput stream, long offset) throws IOException {
    read(stream);
    long off = offset + sizeInBytes();
    Bytes padding = new Bytes("Padding", off % 8);
    padding.read(stream);
    addMember(padding);
    ArrayList list = new ArrayList();
    for(int i = 0;;++i){
      Word word = new Word( "" + i );
      word.read( stream );
      list.add( word );
      if ( word.getValue() == 0 ) break;
    }
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(list.size() * 2);
    DataOutputStream bytesStream = new DataOutputStream( byteArrayOutputStream );
    for (int i = 0; i < list.size(); ++i) {
      Word word = (Word)list.get(i);
      word.write( bytesStream );
    }
    bytesStream.close();

    Txt txt = new Txt("Value", byteArrayOutputStream.toByteArray());
    System.out.println( txt.getText() );
    addMember( txt );

    offset += sizeInBytes();

    System.out.println( "" + offset % 8 );
    System.out.println( "" + offset % 4 );
    System.out.println( "" + offset % 16 );
    long r = offset % 8;
    if ( r > 0 ){
      Bytes padding2 = new Bytes( "Padding2", r );
      padding2.read( stream );
      addMember( padding2 );
    }
  }
}
