// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.CRC32;

public final class Digester {
  /* CRC32 uses the lower 32 bits of the {@code long} type, never returning negative values */
  public static final long INVALID    = 0x8000_0000_0000_0000L;
  public static final long DIRECTORY  = 0x4000_0000_0000_0000L;
  public static final long SYM_LINK   = 0x2000_0000_0000_0000L;
  public static final long EXECUTABLE = 0x1000_0000_0000_0000L;
  public static final long FLAG_MASK  = 0xFFFF_FFFF_0000_0000L;

  public static boolean isFile(long digest) {
    return (digest & FLAG_MASK) == 0;
  }

  public static boolean isSymlink(long digest) {
    return (digest & SYM_LINK) == SYM_LINK;
  }

  public static long digestRegularFile(File file) throws IOException {
    Path path = file.toPath();
    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

    if (attrs.isSymbolicLink()) {
      Path target = Files.readSymbolicLink(path);
      if (target.isAbsolute()) throw new IOException("An absolute link: " + file + " -> " + target);
      return digestStream(new ByteArrayInputStream(target.toString().getBytes(StandardCharsets.UTF_8))) | SYM_LINK;
    }

    if (attrs.isDirectory()) return DIRECTORY;

    long executable = !Utils.IS_WINDOWS && file.canExecute() ? EXECUTABLE : 0;
    try (InputStream in = new BufferedInputStream(Utils.newFileInputStream(file))) {
      return digestStream(in) | executable;
    }
    catch (IOException e) {
      throw new IOException(path.toString(), e);
    }
  }

  public static long digestStream(InputStream in) throws IOException {
    CRC32 crc = new CRC32();
    byte[] BUFFER = new byte[8192];
    int size;
    while ((size = in.read(BUFFER)) != -1) {
      crc.update(BUFFER, 0, size);
    }
    return crc.getValue();
  }
}
