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

package com.pme.exe;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 6:01:16 PM
 */
public class ImageOptionalHeader extends Bin.Structure {

  private final ArrayOfBins<ImageDataDirectory> myImageDataDirectories;
  private final DWord mySectionAlignment;
  private final DWord myFileAlignment;
  private final DWord mySizeOfImage;
  private final DWord mySizeOfHeaders;

  public ImageOptionalHeader() {
    super("Image Optional Header");
    Magic magic = addMember(new Magic());
    addMember(new Byte("MajorLinkerVersion"));
    addMember(new Byte("MinorLinkerVersion"));
    addMember(new DWord("SizeOfCode"));
    addMember(new DWord("SizeOfInitializedData"));
    addMember(new DWord("SizeOfUninitializedData"));
    addMember(new DWord("AddressOfEntryPoint"));
    addMember(new DWord("BaseOfCode"));
    addMember(new Bytes("BaseOfData,ImageBase", 8));
    mySectionAlignment = addMember(new DWord("SectionAlignment"));
    myFileAlignment = addMember(new DWord("FileAlignment"));
    addMember(new Word("MajorOperatingSystemVersion"));
    addMember(new Word("MinorOperatingSystemVersion"));
    addMember(new Word("MajorImageVersion"));
    addMember(new Word("MinorImageVersion"));
    addMember(new Word("MajorSubsystemVersion"));
    addMember(new Word("MinorSubsystemVersion"));
    addMember(new DWord("Win32VersionValue"));
    mySizeOfImage = addMember(new DWord("SizeOfImage"));
    mySizeOfHeaders = addMember(new DWord("SizeOfHeaders"));
    addMember(new DWord("CheckSum"));
    addMember(new Word("Subsystem"));
    addMember(new Word("DllCharacteristics"));
    addMember(new Dependent(magic, DWord.class, LongLong.class, "SizeOfStackReserve"));
    addMember(new Dependent(magic, DWord.class, LongLong.class, "SizeOfStackCommit"));
    addMember(new Dependent(magic, DWord.class, LongLong.class, "SizeOfHeapReserve"));
    addMember(new Dependent(magic, DWord.class, LongLong.class, "SizeOfHeapCommit"));
    addMember(new DWord("LoaderFlags"));
    DWord numberOfImageDataDirectories = addMember(new DWord("NumberOfRvaAndSizes"));
    myImageDataDirectories = new ArrayOfBins<>("ImageDataDirectories", ImageDataDirectory.class, numberOfImageDataDirectories);
    myImageDataDirectories.setCountHolder(numberOfImageDataDirectories);

    addMember(myImageDataDirectories);
  }

  public ArrayOfBins<ImageDataDirectory> getImageDataDirectories() {
    return myImageDataDirectories;
  }

  public DWord getSectionAlignment() {
    return mySectionAlignment;
  }

  public DWord getFileAlignment() {
    return myFileAlignment;
  }

  public DWord getSizeOfImage() {
    return mySizeOfImage;
  }

  public DWord getSizeOfHeaders() {
    return mySizeOfHeaders;
  }

  enum Type {
    PE32,
    PE32Plus,
  }

  private static class Magic extends Word {
    private Magic() {
      super("Magic");
    }

    public Type getType() {
      short value = (short)getValue();
      switch (value) {
        case 0x10B -> {
          return Type.PE32;
        }
        case 0x20B -> {
          return Type.PE32Plus;
        }
        default -> throw new IllegalStateException("Unsupported magic: " + Integer.toHexString(value));
      }
    }
  }

  private static class Dependent extends Value {
    private final Magic myMagic;
    private final DWord myPe32;
    private final LongLong myPe32Plus;

    private Dependent(Magic magic, Class<DWord> pe32, Class<LongLong> pe32plus, String name) {
      super(name);
      myMagic = magic;
      try {
        myPe32 = pe32.getDeclaredConstructor(String.class).newInstance(name);
        myPe32Plus = pe32plus.getDeclaredConstructor(String.class).newInstance(name);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    private Value getCurrent() {
      return switch (myMagic.getType()) {
        case PE32 -> myPe32;
        case PE32Plus -> myPe32Plus;
      };
    }

    @Override
    public long sizeInBytes() {
      return getCurrent().sizeInBytes();
    }

    @Override
    public void read(DataInput stream) throws IOException {
      getCurrent().read(stream);
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      getCurrent().write(stream);
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      getCurrent().report(writer);
    }

    @Override
    public long getValue() {
      return getCurrent().getValue();
    }

    @Override
    public Value setValue(long value) {
      getCurrent().setValue(value);
      return this;
    }
  }
}
