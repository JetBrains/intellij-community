/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipOutputWrapper implements AutoCloseable {
  private final ZipOutputStream myOut;
  private final boolean myCompressed;
  private final Set<String> myDirs = new TreeSet<>();

  public ZipOutputWrapper(OutputStream stream) {
    myOut = new ZipOutputStream(new BufferedOutputStream(stream));
    myCompressed = true;
  }

  public ZipOutputWrapper(OutputStream stream, int compressionLevel) {
    myOut = new ZipOutputStream(new BufferedOutputStream(stream));
    myOut.setLevel(compressionLevel);
    myCompressed = compressionLevel > 0;
  }

  public OutputStream zipStream(String entryPath) {
    return new OptByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        zipBytes(entryPath, this);
      }
    };
  }

  public void zipEntry(ZipEntry entry, InputStream from) throws IOException {
    if (entry.isDirectory()) {
      addDirs(entry.getName(), true);
    }
    else {
      zipEntry(entry.getName(), from);
    }
  }

  public void zipEntry(String entryPath, InputStream from) throws IOException {
    OptByteArrayOutputStream tempOut = new OptByteArrayOutputStream();
    Utils.copyStream(from, tempOut);
    zipBytes(entryPath, tempOut);
  }

  public void zipFile(String entryPath, File file) throws IOException {
    if (file.isDirectory()) {
      throw new IllegalArgumentException("Doesn't make sense");
    }
    try (InputStream from = new BufferedInputStream(new FileInputStream(file))) {
      zipEntry(new ZipEntry(entryPath), from);
    }
  }

  private void zipBytes(String entryPath, OptByteArrayOutputStream byteOut) throws IOException {
    addDirs(entryPath, false);

    ZipEntry entry = new ZipEntry(entryPath);
    if (!myCompressed) {
      entry.setSize(byteOut.size());
      CRC32 crc = new CRC32();
      byteOut.updateChecksum(crc);
      entry.setCrc(crc.getValue());
    }

    myOut.putNextEntry(entry);
    byteOut.writeTo(myOut);
    myOut.closeEntry();
  }

  private void addDirs(String relPath, boolean isDir) {
    if (isDir && !relPath.endsWith("/")) relPath += "/";
    int index = 0;
    while ((index = relPath.indexOf('/', index + 1)) != -1) {
      myDirs.add(relPath.substring(0, index));
    }
  }

  public void finish() throws IOException {
    for (String each : myDirs) {
      if (!each.endsWith("/")) each += "/";
      ZipEntry e = new ZipEntry(each);
      e.setMethod(ZipEntry.STORED);
      e.setSize(0);
      e.setCrc(0);
      myOut.putNextEntry(e);
      myOut.closeEntry();
    }
  }

  @Override
  public void close() throws IOException {
    myOut.close();
  }

  private static class OptByteArrayOutputStream extends ByteArrayOutputStream {
    public void updateChecksum(Checksum cs) {
      cs.update(buf, 0, count);
    }
  }
}