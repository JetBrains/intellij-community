package com.pme.util;

import java.io.*;

public class StreamUtil {
  public static long getOffset(DataInput stream) throws IOException {
    if (stream instanceof OffsetTrackingInputStream) {
      return ((OffsetTrackingInputStream)stream).getOffset();
    }
    if (stream instanceof RandomAccessFile) {
      return ((RandomAccessFile)stream).getFilePointer();
    }
    throw new IOException("OffsetTrackingInputStream or RandomAccessFile expected, got " + stream.getClass().getName());
  }

  public static long getOffset(DataOutput stream) throws IOException {
    if (stream instanceof RandomAccessFile) {
      return ((RandomAccessFile)stream).getFilePointer();
    }
    if (stream instanceof DataOutputStream) {
      return ((DataOutputStream)stream).size();
    }
    throw new IOException("RandomAccessFile or DataOutputStream expected, got " + stream.getClass().getName());
  }

  public static void seek(DataInput stream, long pos) throws IOException {
    if (stream instanceof OffsetTrackingInputStream inputStream) {
      long current = inputStream.getOffset();
      if (current < pos) {
        inputStream.skipBytes((int)(pos - current));
      } else {
        throw new IOException(String.format("Cannot go backwards in OffsetTrackingInputStream: current offset %#010x, seek %#010x", current, pos));
      }
      return;
    }
    if (stream instanceof RandomAccessFile) {
      ((RandomAccessFile)stream).seek(pos);
      return;
    }
    throw new IOException("OffsetTrackingInputStream or RandomAccessFile expected, got " + stream.getClass().getName());
  }
}
