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

import java.io.DataInput;
import java.io.IOException;

/**
 * Date: May 10, 2006
 * Time: 8:09:42 PM
 */
public class StringTableReader extends Bin.Structure{

  public StringTableReader() {
    super("StringTable");
    addMember( new Word( "wLength" ) );
    addMember( new Word( "wValueLength" ) );
    addMember( new Word( "wType" ) );
    addMember( new Bytes( "Bytes", 18 ) );
  }
  public void readWithPadding( DataInput stream, long offset ) throws IOException {
    read(stream);
    StringTableEntry stringTableEntry = new StringTableEntry("CompanyName");
    stringTableEntry.readWithPadding( stream, offset + sizeInBytes() );
    addMember( stringTableEntry );

    StringTableEntry desc = new StringTableEntry("FileDescription");
    desc.readWithPadding( stream, offset + sizeInBytes() + stringTableEntry.sizeInBytes() );
    addMember( desc );

    StringTableEntry fileVersion = new StringTableEntry("FileVersion");
    fileVersion.readWithPadding( stream, offset + sizeInBytes() + stringTableEntry.sizeInBytes() + desc.sizeInBytes() );
    addMember( fileVersion );

    StringTableEntry internalName = new StringTableEntry("InternalName");
    internalName.readWithPadding( stream, offset + sizeInBytes() + stringTableEntry.sizeInBytes() + desc.sizeInBytes() + fileVersion.sizeInBytes() );
    addMember( internalName );

  }
}
