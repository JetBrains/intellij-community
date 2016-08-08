package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Digester {
  // CRC32 will only use the lower 32bits of long, never returning negative values.
  public static long INVALID = -1;
  public static long DIRECTORY = -2;

  public static long digestRegularFile(File file, boolean normalize) throws IOException {
    if (file.isDirectory()) {
      return DIRECTORY;
    }
    InputStream in = new BufferedInputStream(Utils.newFileInputStream(file, normalize));
    try {
      return digestStream(in);
    }
    finally {
      in.close();
    }
  }

  public static long digestZipFile(File file) throws IOException {
    ZipFile zipFile;
    try {
      zipFile = new ZipFile(file);
    } catch (ZipException e) {
      // This was not a zip file...
      return digestRegularFile(file, false);
    }
    try {
      List<ZipEntry> sorted = new ArrayList<>();

      Enumeration<? extends ZipEntry> temp = zipFile.entries();
      while (temp.hasMoreElements()) {
        ZipEntry each = temp.nextElement();
        if (!each.isDirectory()) sorted.add(each);
      }

      Collections.sort(sorted, (o1, o2) -> o1.getName().compareTo(o2.getName()));

      CRC32 crc = new CRC32();
      for (ZipEntry each : sorted) {
        InputStream in = zipFile.getInputStream(each);
        try {
          doDigestStream(in, crc);
        }
        finally {
          in.close();
        }
      }
      return crc.getValue();
    } finally {
      zipFile.close();
    }
  }

  public static long digestStream(InputStream in) throws IOException {
    CRC32 crc = new CRC32();
    doDigestStream(in, crc);
    return crc.getValue();
  }

  private static void doDigestStream(InputStream in, CRC32 crc) throws IOException {
    final byte[] BUFFER = new byte[65536];
    int size;
    while ((size = in.read(BUFFER)) != -1) {
      crc.update(BUFFER, 0, size);
    }
  }
}
