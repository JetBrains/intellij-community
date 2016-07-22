package com.intellij.updater;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipOutputWrapper {
  private final ZipOutputStream myOut;
  private final Set<String> myDirs = new HashSet<>();
  private boolean isCompressed = true;

  public ZipOutputWrapper(OutputStream stream) {
    myOut = new ZipOutputStream(new BufferedOutputStream(stream));
  }

  public void setCompressionLevel(int level) {
    myOut.setLevel(level);
    if (level == 0) {
      myOut.setMethod(ZipEntry.STORED);
      isCompressed = false;
    }
  }

  public OutputStream zipStream(final String entryPath) throws IOException {
    final ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
    return new BufferedOutputStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        tempOut.write(b);
      }

      @Override
      public void close() throws IOException {
        super.close();
        tempOut.close();
        zipBytes(entryPath, tempOut);
      }
    });
  }

  public void zipEntry(ZipEntry entry, InputStream from) throws IOException {
    if (entry.isDirectory()) {
      addDirs(entry.getName(), true);
      return;
    }
    zipEntry(entry.getName(), from);
  }

  public void zipEntry(String entryPath, InputStream from) throws IOException {
    ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
    try {
      Utils.copyStream(from, tempOut);
    }
    finally {
      tempOut.close();
    }
    zipBytes(entryPath, tempOut);
  }

  public void zipBytes(String entryPath, ByteArrayOutputStream byteOut) throws IOException {
    addDirs(entryPath, false);

    ZipEntry entry = new ZipEntry(entryPath);
    if (!isCompressed) {
      entry.setSize(byteOut.size());
      CRC32 crc = new CRC32();
      crc.update(byteOut.toByteArray());
      entry.setCrc(crc.getValue());
    }

    myOut.putNextEntry(entry);
    byteOut.writeTo(myOut);
    myOut.closeEntry();
  }

  public void zipFile(String entryPath, File file) throws IOException {
    if (file.isDirectory()) {
      addDirs(entryPath, true);
      return;
    }

    InputStream from = new BufferedInputStream(new FileInputStream(file));
    try {
      zipEntry(new ZipEntry(entryPath), from);
    }
    finally {
      from.close();
    }
  }

  public void zipFiles(File dir) throws IOException {
    for (File each : dir.listFiles()) {
      addFileToZip(each, null);
    }
  }

  private void addFileToZip(File file, String parentPath) throws IOException {
    String path = parentPath == null ? file.getName() : parentPath + "/" + file.getName();
    zipFile(path, file);

    if (file.isDirectory()) {
      for (File each : file.listFiles()) {
        addFileToZip(each, path);
      }
    }
  }

  private void addDirs(String relPath, boolean isDir) {
    List<String> temp = new ArrayList<>();
    if (isDir && !relPath.endsWith("/")) relPath += "/";
    int index = 0;
    while ((index = relPath.indexOf('/', index + 1)) != -1) {
      temp.add(relPath.substring(0, index));
    }
    myDirs.addAll(temp);
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

    myOut.close();
  }
}
