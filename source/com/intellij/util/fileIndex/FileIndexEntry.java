/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.fileIndex;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;

/**
 * @author nik
 */
public class FileIndexEntry {
  private final long myTimeStamp;

  public FileIndexEntry(final long timestamp) {
    myTimeStamp = timestamp;
  }

  public FileIndexEntry(final DataInputStream stream) throws IOException {
    myTimeStamp = stream.readLong();
  }

  public final long getTimeStamp() {
    return myTimeStamp;
  }

  public void write(DataOutputStream stream) throws IOException {
    stream.writeLong(myTimeStamp);
  }
}
