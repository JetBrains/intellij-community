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

package com.pme.exe;

import com.pme.util.BitsUtil;
import com.pme.util.OffsetTrackingInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Array;

/**
 * Date: Mar 31, 2006
 * Time: 6:17:24 PM
 */
public abstract class Bin {
  private String myName;
  private String myDescription;
  private long myOffset = 0;
  ArrayList<Bin.Value> myOffsetHolders = new ArrayList<Bin.Value>(0);
  ArrayList<Bin.Value> mySizeHolders = new ArrayList<Bin.Value>(0);

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

  public static class Comment extends Bin {
    public Comment(String comment) {
      super(comment);
    }

    public long sizeInBytes() {
      return 0;
    }

    public void read(DataInput stream) throws IOException {
    }

    public void write(DataOutput stream) throws IOException {
    }

    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getName());
    }
  }

  public static class Structure extends Bin {
    private ArrayList<Bin> myMembers = new ArrayList<Bin>(1);
    private HashMap<String, Bin> myMembersMap = new HashMap<String, Bin>(1);

    public Structure(String name) {
      super(name);
    }

    public void resetOffsets(long newOffset) {
      super.resetOffsets(newOffset);
      long offset = getOffset();
      for (Bin bin : myMembers) {
        bin.resetOffsets(offset);
        offset = offset + bin.sizeInBytes();
      }
      updateSizeOffsetHolders();
    }

    public void copyFrom(Bin binStructure) {
      Bin.Structure structure = (Bin.Structure)binStructure;
      ArrayList<Bin> members = structure.getMembers();
      for (Bin bin : members) {
        Bin valueMember = getMember(bin.getName());
        if (valueMember != null) {
          valueMember.copyFrom(bin);
        }
      }
    }

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
    public void addMember(Bin bin, String description) {
      bin.setDescription(description);
      addMember(bin);
    }

    public void addComment(String message) {
      myMembers.add(new Comment(message));
    }

    public Bin insertMember(int index, Bin bin) {
      ArrayList<Bin> list = new ArrayList<Bin>( myMembers.size() + 1 );
      for ( int i = 0; i < index; ++i ){
        list.add( myMembers.get( i ) );
      }
      list.add( bin );
      for ( int i = index; i < myMembers.size(); ++i ){
        list.add( myMembers.get( i ) );
      }
      myMembers = list;
      addMemberToMapOnly(bin);
      return bin;
    }

    public Bin addMember(Bin bin) {
      myMembers.add(bin);
      addMemberToMapOnly(bin);
      return bin;
    }

    //such members are not read by framework
    //it is read by parent in 'read' overrided method
    public void addMemberToMapOnly(Bin bin) {
      myMembersMap.put(bin.getName(), bin);
    }

    public Bin getMember(String name) {
      return myMembersMap.get(name);
    }

    public Bin.Value getValueMember(String name) {
      return (Bin.Value) myMembersMap.get(name);
    }

    public Bin.Structure getStructureMember(String name) {
      return (Bin.Structure) myMembersMap.get(name);
    }

    public long getValue(String name) {
      return ((Bin.Value) myMembersMap.get(name)).getValue();
    }

    public void read(DataInput stream) throws IOException {
      for (Bin bin : myMembers) {
        bin.read(stream);
      }
    }

    public void write(DataOutput stream) throws IOException {
      for (Bin bin : myMembers) {
        bin.write(stream);
      }
    }

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

    public void copyFrom(Bin value) {
      setValue(((Value)value).getValue());
    }

    public long getRawValue() {
      return myValue;
    }

    public Value setRawValue(long value) {
      myValue = value;
      return this;
    }
  }

  public static class Byte extends Value {
    public Byte(String name) {
      super(name);
    }

    public long sizeInBytes() {
      return 1;
    }

    public long getValue() {
      return getRawValue();
    }
    public Value setValue(long value) {
      setRawValue(value);
      return this;
    }

    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readByte());
    }

    public void write(DataOutput stream) throws IOException {
      stream.writeByte((byte) getRawValue());
    }

    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getDescription(), (byte) getValue());
    }

  }

  public static class Word extends Value {
    public Word() {
      super("");
    }

    public Word(String name) {
      super(name);
    }

    public long sizeInBytes() {
      return 2;
    }

    public long getValue() {
      return BitsUtil.revertBytesOfShort((short) super.getRawValue());
    }

    public Value setValue(long value) {
      setRawValue(BitsUtil.revertBytesOfShort((short) value));
      return this;
    }

    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readShort());
    }

    public void write(DataOutput stream) throws IOException {
      stream.writeShort((short) getRawValue());
    }

    public void report(OutputStreamWriter writer) throws IOException {
      short sh = (short) getValue();
      _report(writer, getDescription(), sh);
    }
    public String toString() {
      return BitsUtil.shortToHexString( (int)getValue() );
    }
  }

  public static class DWord extends Value {
    public DWord(String name) {
      super(name);
    }

    public long sizeInBytes() {
      return 4;
    }

    public Value setValue(long value) {
      setRawValue(BitsUtil.revertBytesOfInt((int) value));
      return this;
    }

    public long getValue() {
      return BitsUtil.revertBytesOfInt((int) super.getRawValue());
    }

    public void read(DataInput stream) throws IOException {
      setRawValue(stream.readInt());
    }

    public void write(DataOutput stream) throws IOException {
      stream.writeInt((int) getRawValue());
    }

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
      return BitsUtil.revertBytesOfLong(getRawValue());
    }

    @Override
    public Value setValue(long value) {
      setRawValue(BitsUtil.revertBytesOfLong(value));
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
    private int myBytes;

    public Padding(int bytes) {
      super("Padding");
      myBytes = bytes;
    }

    @Override
    public long sizeInBytes() {
      return bytesToSkip(getOffset());
    }

    @Override
    public void read(DataInput stream) throws IOException {
      if (stream instanceof OffsetTrackingInputStream) {
        long offset = ((OffsetTrackingInputStream) stream).getOffset();
        int skip = bytesToSkip(offset);
        if (skip > 0) {
          stream.skipBytes(skip);
        }
      }
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
    public void report(OutputStreamWriter writer) throws IOException {
    }
  }

  public static class Txt extends Bin {
    private StringBuffer myBuffer = new StringBuffer();
    private Bin.Value mySize;
    private byte[] myBytes;

    public Txt(String name, byte[] bytes) {
      super(name);
      myBytes = bytes;
      mySize = new DWord("").setValue(bytes.length);
      setValue();
    }

    public Txt(String name, String string) {
      super(name);
      myBytes = new byte[string.length() * 2];
      byte[] bytes = string.getBytes();
      for (int i = 0; i < bytes.length; ++i) {
        myBytes[i * 2] = bytes[i];
        myBytes[i * 2 + 1] = 0;
      }
      mySize = new DWord("").setValue(myBytes.length);
      setValue();
    }

    public Txt(String name, Bin.Value size) {
      super(name);
      mySize = size;
    }

    public Txt(String name, int size) {
      this(name, new DWord("size").setValue(size));
    }

    public String getText() {
      return myBuffer.toString();
    }

    public long sizeInBytes() {
      return mySize.getValue();
    }

    private void setValue(){
      for (int i = 0; i < mySize.getValue(); ++i) {
        int b = BitsUtil.unsignedByte(myBytes[i]);
        if (b != 0) {
          myBuffer.append((char) b);
        }
      }
    }

    public void read(DataInput stream) throws IOException {
      myBuffer.setLength(0);
      myBytes = new byte[(int) mySize.getValue()];
      for (int i = 0; i < mySize.getValue(); ++i) {
        myBytes[i] = stream.readByte();
      }
      setValue();
    }

    public void write(DataOutput stream) throws IOException {
      stream.write(myBytes);
    }

    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, myBuffer.toString());
    }
  }

  public static class WChar extends Bin {
    private String myValue;

    public WChar(String name) {
      super(name);
    }

    @Override
    public long sizeInBytes() {
      return myValue.length() * 2 + 2;
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
        stream.writeShort(BitsUtil.revertBytesOfShort((short) myValue.charAt(i)));
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
  }

  public static class Bytes extends Bin {
    private byte[] myBytes;
    private Value myStartOffset;
    private Value mySize;
    private int myBytesInRow = 16;

    public Bytes(String name, Bin.Value size) {
      super(name);
      mySize = size;
    }

    public Bytes(String name, long size) {
      super(name);
      mySize = new DWord("size").setValue(size);
    }

    public Bytes(String name, Bin.Value startOffset, Bin.Value size) {
      super(name);
      reset(startOffset, size);
    }

    public void reset(int startOffset, int size) {
      reset(new DWord("startOffset").setValue(startOffset), new DWord("size").setValue(size));
    }

    public void reset(Bin.Value startOffset, Bin.Value size) {
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

    public long sizeInBytes() {
      return mySize.getValue();
    }

    public void read(DataInput stream) throws IOException {
      if (myStartOffset != null) {
        RandomAccessFile file = (RandomAccessFile) stream;
        file.seek(myStartOffset.getValue());
      }
      myBytes = new byte[(int) mySize.getValue()];
      stream.readFully(myBytes);
    }

    public void write(DataOutput stream) throws IOException {
      stream.write(myBytes, 0, (int) sizeInBytes());
    }

    private StringBuffer myBuffer = new StringBuffer();

    public void report(OutputStreamWriter writer) throws IOException {
      _report(writer, getName());
      _report(writer, "Number of bytes: " + mySize.getValue());
      int rowCount = (myBytes.length / myBytesInRow);
      if (myBytes.length % myBytesInRow != 0) {
        rowCount++;
      }
      int byteCount = 0;
      for (int i = 0; i < rowCount; i++) {
        myBuffer.setLength(0);
        myBuffer.append("\n");
        for (int j = 0; j < myBytesInRow && byteCount < myBytes.length; j++) {
          byte aByte = myBytes[byteCount++];
          myBuffer.append(" ").append(BitsUtil.byteToHexString(aByte));
        }
        writer.write(myBuffer.toString());
      }
    }
  }

  public static class ArrayOfBins<T extends Bin> extends Bin {
    private Bin[] myValues;
    private Bin.Value mySize;
    private Class myClass;
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

    public void addBin( Bin bin ){
      Bin[] newArray = new Bin[myValues.length+1];
      System.arraycopy( myValues, 0, newArray, 0, myValues.length );
      newArray[myValues.length] = bin;
      myValues = newArray;
      mySize.setValue( mySize.getValue() + 1 );
    }

    public void copyFrom(Bin bin) {
      ArrayOfBins value = (ArrayOfBins)bin;
      for (int i = 0; i < myValues.length; i++) {
        myValues[i].copyFrom( value.get(i) );
      }
    }

    public void setCountHolder(Value countHolder) {
      myCountHolder = countHolder;
    }

    private void init() {
      myValues = (Bin[]) Array.newInstance(myClass, (int) mySize.getValue());

      for (int i = 0; i < myValues.length; i++) {
        try {
          Bin bin = (Bin) myClass.newInstance();
          bin.setName("[" + i + "]");
          myValues[i] = bin;
        } catch (InstantiationException e) {
          throw new RuntimeException(e.getMessage());
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e.getMessage());
        }
      }
    }

    public void resetOffsets(long newOffset) {
      super.resetOffsets(newOffset);
      long offset = getOffset();
      if (myCountHolder != null) {
        myCountHolder.setValue(myValues.length);
      }
      for (Bin bin : myValues) {
        bin.resetOffsets(offset);
        offset = offset + bin.sizeInBytes();
      }
    }

    public int size() {
      return myValues.length;
    }

    public Bin[] getArray() {
      return myValues;
    }

    public T get(int index) {
      //noinspection unchecked
      return (T) myValues[index];
    }

    public long sizeInBytes() {
      int size = 0;
      for (Bin value : myValues) {
        size += value.sizeInBytes();
      }
      return size;
    }

    public void read(DataInput stream) throws IOException {
      init();
      for (Bin value : myValues) {
        value.read(stream);
      }
    }

    public void write(DataOutput stream) throws IOException {
      for (Bin value : myValues) {
        value.write(stream);
      }
    }

    public void report(OutputStreamWriter writer) throws IOException {
      writer.write("\n" + "Array size: " + myValues.length);
      for (Bin value : myValues) {
        value.report(writer);
      }
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
