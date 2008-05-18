/*
 * @author max
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DataInputOutputUtil {
  public final static long timeBase = 33l * 365l * 24l * 3600l * 1000l;

  private DataInputOutputUtil() {}

  public static void skipUTF(DataInput record) throws IOException {
    record.skipBytes(record.readUnsignedShort());
  }

  public static StringRef readNAME(DataInput record, PersistentStringEnumerator nameStore) throws IOException {
    return StringRef.fromStream(record, nameStore);
  }

  public static void writeNAME(DataOutput record, final String name, PersistentStringEnumerator nameStore) throws IOException {
    final int nameId = name != null ? nameStore.enumerate(name) : 0;
    record.writeByte(nameId & 0xFF);
    writeINT(record, (nameId >> 8));
  }

  public static void skipNAME(DataInput record) throws IOException {
    record.readUnsignedByte();
    readINT(record);
  }

  public static void skipINT(DataInput record) throws IOException {
    readINT(record);
  }

  public static int readINT(DataInput record) throws IOException {
    final int val = record.readUnsignedByte();
    if (val < 192) {
      return val;
    }

    for (int res = val - 192, sh = 6; ; sh += 7) {
      int next = record.readUnsignedByte();
      res |= (next & 0x7F) << sh;
      if ((next & 0x80) == 0) {
        return res;
      }
    }
  }

  public static void writeINT(DataOutput record, int val) throws IOException {
    /*
    if (0 <= val && val < 255)
      record.writeByte(val);
    else {
      record.writeByte(255);
      record.writeInt(val);
    }
    */
    if (0 <= val && val < 192) {
      record.writeByte(val);
    }
    else {
      record.writeByte(192 + (val & 0x3F));
      val >>>= 6;
      while (val >= 128) {
        record.writeByte((val & 0x7F) | 0x80);
        val >>>= 7;
      }
      record.writeByte(val);
    }
  }

  public static void skipSINT(DataInput record) throws IOException {
    readSINT(record);
  }

  public static int readSINT(DataInput record) throws IOException {
    return readINT(record) - 64;
  }

  public static void writeSINT(DataOutput record, int val) throws IOException {
    writeINT(record, val + 64);
  }

  public static void writeTIME(DataOutput record, long timestamp) throws IOException {
    long relStamp = timestamp - timeBase;
    if (relStamp < 0 || relStamp >= 0xFF00000000l) {
      record.writeByte(255);
      record.writeLong(timestamp);
    }
    else {
      record.writeByte((int)(relStamp >> 32));
      record.writeByte((int)(relStamp >> 24));
      record.writeByte((int)(relStamp >> 16));
      record.writeByte((int)(relStamp >> 8));
      record.writeByte((int)(relStamp >> 0));
    }
  }

  public static long readTIME(DataInput record) throws IOException {
    final int first = record.readUnsignedByte();
    if (first == 255) {
      return record.readLong();
    }
    else {
      final int second = record.readUnsignedByte();

      final int third = record.readUnsignedByte() << 16;
      final int fourth = record.readUnsignedByte() << 8;
      final int fifth = record.readUnsignedByte();
      return ((((long)((first << 8) | second)) << 24) | (third | fourth | fifth)) + timeBase;
    }
  }
}