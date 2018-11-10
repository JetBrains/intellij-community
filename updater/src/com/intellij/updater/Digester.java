// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Digester {
  // CRC32 will only use the lower 32bits of long, never returning negative values.
  public static final long INVALID    = 0x8000_0000_0000_0000L;
  public static final long DIRECTORY  = 0x4000_0000_0000_0000L;
  private static final long LINK_MASK = 0x2000_0000_0000_0000L;
  private static final long FLAG_MASK = 0xFFFF_FFFF_0000_0000L;

  public static boolean isFile(long digest) {
    return (digest & FLAG_MASK) == 0;
  }

  public static boolean isSymlink(long digest) {
    return (digest & LINK_MASK) == LINK_MASK;
  }

  public static long digestRegularFile(File file, boolean normalize) throws IOException {
    Path path = file.toPath();
    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

    if (attrs.isSymbolicLink()) {
      Path target = Files.readSymbolicLink(path);
      if (target.isAbsolute()) throw new IOException("Absolute link: " + file + " -> " + target);
      return digestStream(new ByteArrayInputStream(target.toString().getBytes(StandardCharsets.UTF_8))) | LINK_MASK;
    }

    if (attrs.isDirectory()) return DIRECTORY;

    try (InputStream in = new BufferedInputStream(Utils.newFileInputStream(file, normalize))) {
      return digestStream(in);
    }
  }

  public static long digestZipFile(File file) throws IOException {
    ZipFile zipFile;
    try {
      zipFile = new ZipFile(file);
    }
    catch (ZipException e) {
      // This was not a zip file...
      return digestRegularFile(file, false);
    }
    try {
      List<ZipEntry> sorted = new ArrayList<>();

      Enumeration<? extends ZipEntry> temp = zipFile.entries();
      while (temp.hasMoreElements()) {
        ZipEntry each = temp.nextElement();
        if (!each.isDirectory()) {
          sorted.add(each);
        }
      }

      Collections.sort(sorted, Comparator.comparing(ZipEntry::getName));

      CRC32 crc = new CRC32();
      for (ZipEntry each : sorted) {
        try (InputStream in = zipFile.getInputStream(each)) {
          doDigestStream(in, crc);
        }
      }
      return crc.getValue();
    }
    finally {
      zipFile.close();
    }
  }

  public static long digestStream(InputStream in) throws IOException {
    CRC32 crc = new CRC32();
    doDigestStream(in, crc);
    return crc.getValue();
  }

  private static void doDigestStream(InputStream in, CRC32 crc) throws IOException {
    byte[] BUFFER = new byte[8192];
    int size;
    while ((size = in.read(BUFFER)) != -1) {
      crc.update(BUFFER, 0, size);
    }
  }
}