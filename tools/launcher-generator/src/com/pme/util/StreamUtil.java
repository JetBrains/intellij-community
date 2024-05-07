// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.pme.util;

import java.io.*;

public final class StreamUtil {
  public static long getOffset(DataInput stream) throws IOException {
    if (stream instanceof OffsetTrackingInputStream otis) return otis.getOffset();
    if (stream instanceof RandomAccessFile raf) return raf.getFilePointer();
    throw new IOException("OffsetTrackingInputStream or RandomAccessFile expected, got " + stream.getClass().getName());
  }

  public static long getOffset(DataOutput stream) throws IOException {
    if (stream instanceof RandomAccessFile raf) return raf.getFilePointer();
    if (stream instanceof DataOutputStream dos) return dos.size();
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
    if (stream instanceof RandomAccessFile raf) {
      raf.seek(pos);
      return;
    }
    throw new IOException("OffsetTrackingInputStream or RandomAccessFile expected, got " + stream.getClass().getName());
  }
}
