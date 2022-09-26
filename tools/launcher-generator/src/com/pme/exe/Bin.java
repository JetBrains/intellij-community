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

import com.pme.util.BitsUtil;
import com.pme.util.StreamUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 6:17:24 PM
 */
public abstract class Bin {
  private String myName;
  private String myDescription;
  private long myOffset = 0;
  final ArrayList<Bin.Value> myOffsetHolders = new ArrayList<>(0);
  final ArrayList<Bin.Value> mySizeHolders = new ArrayList<>(0);

  public Bin(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getDescription() {
    if (myDescription != null) {
      return myDescription;
    }
    return myName;
  }

  public long getOffset() {
    return myOffset;
  }

  public void resetOffsets(long offset) {
    myOffset = offset;
    updateSizeOffsetHolders();
  }

  protected void updateSizeOffsetHolders() {
    for (Value holder : myOffsetHolders) {
      holder.setValue(myOffset);
    }
    for (Value holder : mySizeHolders) {
      holder.setValue(sizeInBytes());
    }
  }

  public void copyFrom(Bin value) {
  }

  public void addOffsetHolder(Value offsetHolder) {
    myOffsetHolders.add( offsetHolder );
  }

  public void addSizeHolder(Value sizeHolder) {
    mySizeHolders.add( sizeHolder );
  }

  public abstract long sizeInBytes();

  public void setDescription(String description) {
    myDescription = description;
  }

  public abstract void read(DataInput stream) throws IOException;

  public abstract void write(DataOutput stream) throws IOException;

  public abstract void report(OutputStreamWriter writer) throws IOException;

  public static class Structure extends Bin {
    private final ArrayList<Bin> myMembers = new ArrayList<>(1);
    private final HashMap<String, Bin> myMembersMap = new LinkedHashMap<>(1);

    public Structure(String name) {
      super(name);
    }

    @Override
    public void resetOffsets(long newOffset) {
      super.resetOffsets(newOffset);
      long offset = getOffset();
      for (Bin bin : myMembers) {
        bin.resetOffsets(offset);
        offset += bin.sizeInBytes();
      }
      updateSizeOffsetHolders();
    }

    @Override
    public void copyFrom(Bin binStructure) {
      Bin.Structure structure = (Bin.Structure)binStructure;
      ArrayList<Bin> members = structure.getMembers();
      for (Bin bin : members) {
        Bin valueMember = myMembersMap.get(bin.getName());
        if (valueMember != null) {
          valueMember.copyFrom(bin);
        }
      }
    }

    @Override
    public long sizeInBytes() {
      long size = 0;
      for (Bin bin : myMembers) {
        size += bin.sizeInBytes();
      }
      return size;
    }

    public ArrayList<Bin> getMembers() {
      return myMembers;
    }

    public <T extends Bin> T addMember(T bin, String description) {
      bin.setDescription(description);
      return addMember(bin);
    }

    public <T extends Bin> T addMember(T bin) {
      myMembers.add(bin);
      addMemberToMapOnly(bin);
      return bin;
    }

    //such members are not read by framework
    //it is read by parent in overrode 'read' method
    public void addMemberToMapOnly(Bin bin) {
      myMembersMap.put(bin.getName(), bin);
    }

    @Override
    public void read(DataInput stream) throws IOException {
      for (Bin bin : myMembers) {
        bin.read(stream);
      }
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      for (Bin bin : myMembers) {
        bin.write(stream);
      }
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, "--- '" + getName() + "' Structure --- size = " + sizeInBytes());
      _report(writer, "{Offset = " + Long.toHexString(getOffset()) + "}");
      for (Bin bin : myMembers) {
        bin.report(writer);
      }
      _report(writer, "--- End Of '" + getName() + "' Structure ---");
    }
  }

  public abstract static class Value extends Bin {
    private long myValue;

    public Value(String name) {
      super(name);
    }

    public abstract long getValue();
    public abstract Value setValue(long value);

    @Override
    public void copyFrom(Bin value) {
      setValue(((Value)value).getValue());
    }

    public long getRawValue() {
      return myValue;
    }

    public void setRawValue(long value) {
      myValue = value;
    }
  }

  public static abstract class ReadOnlyValue extends Value {
    public ReadOnlyValue(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      throw new IllegalStateException();
    }

    @Override
    public void read(DataInput stream) {
      throw new IllegalStateException();
    }

    @Override
    public void write(DataOutput stream) {
      throw new IllegalStateException();
    }

    @Override
    public void report(OutputStreamWriter writer) {
      throw new IllegalStateException();
    }

    @Override
    public Value setValue(long value) {
      throw new IllegalStateException();
    }
  }

  public static class Byte extends Value {
    public Byte(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      return 1;
    }

    @Override
    public long getValue() {
      return getRawValue();
    }

    @Override
    public Byte setValue(long value) {
      setRawValue(value);
      return this;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readByte());
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.writeByte((byte)getRawValue());
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getDescription(), (byte)getValue());
    }

    public String toString() {
      return BitsUtil.byteToHexString((int)getValue());
    }
  }

  public static class Word extends Value {
    public Word() {
      super("");
    }

    public Word(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      return 2;
    }

    @Override
    public long getValue() {
      return Short.toUnsignedLong(Short.reverseBytes((short) super.getRawValue()));
    }

    @Override
    public Word setValue(long value) {
      setRawValue(Short.toUnsignedLong(Short.reverseBytes((short)value)));
      return this;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readShort());
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.writeShort((short) getRawValue());
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      short sh = (short) getValue();
      _report(writer, getDescription(), sh);
    }
    public String toString() {
      return BitsUtil.shortToHexString( (int)getValue() );
    }
  }

  public static class DWord extends Value {
    public DWord() {
      super("");
    }

    public DWord(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      return 4;
    }

    @Override
    public DWord setValue(long value) {
      setRawValue(Integer.toUnsignedLong(Integer.reverseBytes((int)value)));
      return this;
    }

    @Override
    public long getValue() {
      return Integer.toUnsignedLong(Integer.reverseBytes((int) super.getRawValue()));
    }

    @Override
    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readInt());
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.writeInt((int) getRawValue());
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getDescription(), (int) getValue());
    }
    public String toString() {
      return BitsUtil.intToHexString( (int)getValue() );
    }
  }

  public static class LongLong extends Value {
    public LongLong(String name) {
      super(name);
    }

    @Override
    public long getValue() {
      return Long.reverseBytes(getRawValue());
    }

    @Override
    public LongLong setValue(long value) {
      setRawValue(Long.reverseBytes(value));
      return this;
    }

    @Override
    public long sizeInBytes() {
      return 8;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readLong());
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.writeLong(getRawValue());
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getDescription() + " : " + Long.toHexString(getValue()));
    }
  }

  public static class Padding extends Bin {
    private final int myBytes;

    public Padding(String name, int bytes) {
      super(name);
      myBytes = bytes;
    }

    @Override
    public long sizeInBytes() {
      return bytesToSkip(getOffset());
    }

    @Override
    public void read(DataInput stream) throws IOException {
      long offset = StreamUtil.getOffset(stream);
      int skip = bytesToSkip(offset);
      if (skip > 0) {
        stream.skipBytes(skip);
      }
      //resetOffsets(offset);
    }

    private int bytesToSkip(long offset) {
      int offsetMask = myBytes-1;
      long offsetBits = offset & offsetMask;
      return offsetBits == 0 ? 0 : (int) (myBytes - offsetBits);
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      int skip = bytesToSkip(getOffset());
      for (int i = 0; i < skip; i++) {
        stream.writeByte(0);
      }
    }

    @Override
    public void report(OutputStreamWriter writer) {
    }

    @Override
    public String toString() {
      return "Padding{" + getName() + "," + myBytes + '}';
    }
  }

  /**
   * Fixed size character (1 byte) string, UTF-8
   */
  public static class CharStringFS extends Bin {
    private final StringBuffer myBuffer = new StringBuffer();
    private final Bin.Value mySize;
    private byte[] myBytes;

    public CharStringFS(String name, Bin.Value size) {
      super(name);
      mySize = size;
    }

    public CharStringFS(String name, int size) {
      this(name, new DWord("size").setValue(size));
    }

    @Override
    public long sizeInBytes() {
      return mySize.getValue();
    }

    @Override
    public void read(DataInput stream) throws IOException {
      long size = mySize.getValue();
      myBuffer.setLength(0);
      myBytes = new byte[(int)size];
      for (int i = 0; i < size; ++i) {
        myBytes[i] = stream.readByte();
      }
      for (int i = 0; i < size; ++i) {
        int b = java.lang.Byte.toUnsignedInt(myBytes[i]);
        if (b != 0) {
          myBuffer.append((char)b);
        }
      }
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.write(myBytes);
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, myBuffer.toString());
    }

    public String getValue() {
      return myBuffer.toString();
    }

    @Override
    public String toString() {
      return "CharStringFS{size=" + mySize.getValue() + ", value=" + getValue() + "}";
    }
  }

  /**
   * Size-prefixed wide character (2 bytes) string, UTF-16.
   * <p>
   * Size is {@linkplain Word} by default.
   */
  public static class WCharStringSP extends Bin {
    private static final String EMPTY_STRING = "";
    private final Value mySize;
    private String myValue;

    public WCharStringSP() {
      this(new Word());
    }

    public WCharStringSP(Value size) {
      super("");
      mySize = size;
    }

    public String getValue() {
      return myValue;
    }

    public void setValue(String value) {
      myValue = value;
      mySize.setValue(value.length());
    }

    @Override
    public long sizeInBytes() {
      return mySize.sizeInBytes() + mySize.getValue() * 2;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      mySize.read(stream);
      long size = mySize.getValue();
      if (size == 0) {
        myValue = EMPTY_STRING;
        return;
      }
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < size; ++i) {
        char c = BitsUtil.readChar(stream);
        builder.append(c);
      }
      myValue = builder.toString();
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      assert mySize.getValue() == myValue.length();
      mySize.write(stream);
      for (int i = 0; i < myValue.length(); i++) {
        stream.writeShort(Short.toUnsignedInt(Short.reverseBytes((short)myValue.charAt(i))));
      }
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, myValue);
    }

    @Override
    public String toString() {
      return "WCharStringSP{" +
             "size=" + mySize +
             ", value=" + myValue +
             '}';
    }
  }

  /**
   * Null-terminated wide character (2 bytes) string, UTF-16
   */
  public static class WCharStringNT extends Bin {
    private String myValue;

    public WCharStringNT(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      return myValue.length() * 2L + 2;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      StringBuilder valueBuilder = new StringBuilder();
      while(true) {
        char c = BitsUtil.readChar(stream);
        if (c == 0) break;
        valueBuilder.append(c);
      }
      myValue = valueBuilder.toString();
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      for (int i = 0; i < myValue.length(); i++) {
        stream.writeShort(Short.toUnsignedInt(Short.reverseBytes((short)myValue.charAt(i))));
      }
      stream.writeShort(0);
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, myValue);
    }

    public String getValue() {
      return myValue;
    }

    public void setValue(String value) {
      myValue = value;
    }

    @Override
    public String toString() {
      return "WCharStringNT{value=" + myValue + '}';
    }
  }

  public static class Bytes extends Bin {
    private byte[] myBytes;
    private final Value myStartOffset;
    private final Value mySize;
    private static final int ourBytesInRow = 16;

    public Bytes(String name, Bin.Value size) {
      super(name);
      myStartOffset = null;
      mySize = size;
    }

    public Bytes(String name, long size) {
      super(name);
      myStartOffset = null;
      mySize = new DWord("size").setValue(size);
    }

    public Bytes(String name, Bin.Value startOffset, Bin.Value size) {
      super(name);
      myStartOffset = startOffset;
      mySize = size;
    }

    public byte[] getBytes() {
      return myBytes;
    }

    public void setBytes(byte[] bytes) {
      setBytes(bytes, bytes.length);
    }

    public void setBytes(byte[] bytes, int size) {
      if (bytes.length < size) {
        throw new RuntimeException("bytes.length < size");
      }
      myBytes = bytes;
      mySize.setValue(size);
    }

    @Override
    public long sizeInBytes() {
      return mySize.getValue();
    }

    @Override
    public void read(DataInput stream) throws IOException {
      if (myStartOffset != null) {
        long offset = myStartOffset.getValue();
        long streamOffset = StreamUtil.getOffset(stream);
        if (streamOffset != offset) {
          if (offset > streamOffset) {
            //noinspection UseOfSystemOutOrSystemErr
            System.err.printf("WARN: non-continuous read: reading offset %#x, current stream offset %#x %n", offset, streamOffset);
          } else {
            //noinspection UseOfSystemOutOrSystemErr
            System.err.printf("WARN: out of order read: reading offset %#x, current stream offset %#x %n", offset, streamOffset);
          }
          StreamUtil.seek(stream, offset);
        }
      }
      myBytes = new byte[(int) mySize.getValue()];
      stream.readFully(myBytes);
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      stream.write(myBytes, 0, (int) sizeInBytes());
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getName());
      _report(writer, "Number of bytes: " + mySize.getValue());
      int rowCount = (myBytes.length / ourBytesInRow);
      if (myBytes.length % ourBytesInRow != 0) {
        rowCount++;
      }
      int byteCount = 0;
      StringBuilder myBuffer = new StringBuilder();
      for (int i = 0; i < rowCount; i++) {
        myBuffer.setLength(0);
        myBuffer.append("\n");
        for (int j = 0; j < ourBytesInRow && byteCount < myBytes.length; j++) {
          byte aByte = myBytes[byteCount++];
          myBuffer.append(" ").append(BitsUtil.byteToHexString(aByte));
        }
        writer.write(myBuffer.toString());
      }
    }

    @Override
    public String toString() {
      return "Bytes{" +
             "StartOffset=" + myStartOffset +
             ", Size=" + mySize +
             '}';
    }
  }

  public static class ArrayOfBins<T extends Bin> extends Bin implements Iterable<T> {
    private ArrayList<T> myValues;
    private final Bin.Value mySize;
    private final Class<T> myClass;
    private Bin.Value myCountHolder = null;

    public ArrayOfBins(String name, Class<T> cl, Bin.Value size) {
      super(name);
      myClass = cl;
      mySize = size;
      init();
    }

    public ArrayOfBins(String name, Class<T> cl, int size) {
      this(name, cl, new DWord("size").setValue(size));
    }

    public void addBin(T bin) {
      myValues.add(bin);
      if (myCountHolder != null) {
        myCountHolder.setValue(myValues.size());
      }
    }

    @Override
    public void copyFrom(Bin bin) {
      //noinspection unchecked
      ArrayOfBins<T> value = (ArrayOfBins<T>)bin;
      for (int i = 0; i < myValues.size(); i++) {
        myValues.get(i).copyFrom(value.myValues.get(i));
      }
    }

    public void setCountHolder(Value countHolder) {
      myCountHolder = countHolder;
    }

    private void init() {
      int size = (int)mySize.getValue();
      myValues = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
        try {
          T bin = myClass.getDeclaredConstructor().newInstance();
          bin.setName("[" + i + "]");
          myValues.add(bin);
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
          throw new RuntimeException(e.getMessage());
        }
      }
    }

    @Override
    public void resetOffsets(long newOffset) {
      super.resetOffsets(newOffset);
      long offset = getOffset();
      if (myCountHolder != null) {
        myCountHolder.setValue(myValues.size());
      }
      for (Bin bin : myValues) {
        bin.resetOffsets(offset);
        offset += bin.sizeInBytes();
      }
    }

    public int size() {
      return myValues.size();
    }

    public T get(int index) {
      return myValues.get(index);
    }

    @Override
    public long sizeInBytes() {
      int size = 0;
      for (Bin value : myValues) {
        size += value.sizeInBytes();
      }
      return size;
    }

    @Override
    public void read(DataInput stream) throws IOException {
      init();
      for (Bin value : myValues) {
        value.read(stream);
      }
    }

    @Override
    public void write(DataOutput stream) throws IOException {
      for (Bin value : myValues) {
        value.write(stream);
      }
    }

    @Override
    public void report(OutputStreamWriter writer) throws IOException {
      writer.write("\n" + getName() + " array size: " + myValues.size());
      for (Bin value : myValues) {
        value.report(writer);
      }
    }

    @Override
    public Iterator<T> iterator() {
      return myValues.iterator();
    }
  }

  protected void _report(OutputStreamWriter buffer, String name, int value) throws IOException {
    buffer.write("\n" + name + " : " + BitsUtil.intToHexString(value));
  }

  protected void _report(OutputStreamWriter buffer, String name, short value) throws IOException {
    buffer.write("\n" + name + " : " + BitsUtil.shortToHexString(value));
  }

  protected void _report(OutputStreamWriter buffer, String name, byte value) throws IOException {
    buffer.write("\n" + name + " : " + BitsUtil.byteToHexString(value));
  }

  protected void _report(OutputStreamWriter buffer, String message) throws IOException {
    buffer.write("\n" + message);
  }
}
