// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.updater.Runner.LOG;

public final class Utils {
  private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
  public static final boolean IS_WINDOWS = OS_NAME.startsWith("windows");
  public static final boolean IS_MAC = OS_NAME.startsWith("mac");

  private static final long REQUIRED_FREE_SPACE = Long.getLong("idea.required.space", 2_000_000_000L);

  private static final int BUFFER_SIZE = 8192;  // to minimize native memory allocations for I/O operations

  private static final CopyOption[] COPY_STANDARD = {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES};
  private static final CopyOption[] COPY_REPLACE = {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING};
  private static final CopyOption[] MOVE_STANDARD = {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.ATOMIC_MOVE};
  private static final CopyOption[] MOVE_REPLACE = {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING};

  private static final boolean WIN_UNPRIVILEGED = IS_WINDOWS && Boolean.getBoolean("idea.unprivileged.process");

  private static File myTempDir;

  public synchronized static File getTempFile(String name) throws IOException {
    if (myTempDir == null) {
      String path = System.getProperty("java.io.tmpdir");
      if (path == null) throw new IllegalArgumentException("System property `java.io.tmpdir` is not defined");

      Path dir = Path.of(path);
      if (!Files.isDirectory(dir)) throw new IOException("Not a directory: " + dir);

      if (REQUIRED_FREE_SPACE > 0) {
        FileStore fs = Files.getFileStore(dir);
        if (fs.getUsableSpace() < REQUIRED_FREE_SPACE) {
          throw new IOException("Not enough free space on '" + fs + "' (" + (REQUIRED_FREE_SPACE / 1_000_000) + " MB required");
        }
      }

      myTempDir = Files.createTempDirectory(dir, "idea.updater.files.").toFile();
      LOG.info("created a working directory: " + myTempDir);
    }

    File myTempFile;
    int index = 0;
    do {
      myTempFile = new File(myTempDir, name + ".tmp." + index++);
    }
    while (myTempFile.exists());
    return myTempFile;
  }

  public synchronized static void cleanup() throws IOException {
    if (myTempDir == null) return;
    delete(myTempDir);
    LOG.info("deleted a working directory: " + myTempDir.getPath());
    myTempDir = null;
  }

  public static void delete(File file) throws IOException {
    delete(file.toPath());
  }

  public static void delete(Path file) throws IOException {
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      Files.walkFileTree(file, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          tryDelete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          tryDelete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  private static void tryDelete(Path path) throws IOException {
    IOException lastError = null;

    for (int i = 0; i < 10; i++) {
      try {
        Files.delete(path);
        LOG.info("deleted: " + path);
        return;
      }
      catch (NoSuchFileException e) {
        LOG.info("already deleted: " + path);
        return;
      }
      catch (AccessDeniedException e) {
        lastError = e;
        try {
          DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class);
          if (view != null && view.readAttributes().isReadOnly()) {
            view.setReadOnly(false);
            continue;
          }
        }
        catch (IOException ignore) { }
      }
      catch (IOException e) {
        lastError = e;
      }

      pause(10);
    }

    throw new IOException("Cannot delete: " + path, lastError);
  }

  public static boolean isExecutable(File file) {
    return file.canExecute();
  }

  public static void setExecutable(Path file) throws IOException {
    setExecutable(file.toFile());
  }

  public static void setExecutable(File file) throws IOException {
    LOG.info("Setting executable permissions for: " + file);
    if (!file.setExecutable(true, false)) {
      throw new IOException("Cannot set executable permissions for: " + file);
    }
  }

  public static boolean isLink(File file) {
    return Files.isSymbolicLink(file.toPath());
  }

  public static String readLink(File link) throws IOException {
    return Files.readSymbolicLink(link.toPath()).toString();
  }

  public static void createLink(String target, File link) throws IOException {
    Path path = link.toPath();
    Files.deleteIfExists(path);
    Files.createSymbolicLink(path, Path.of(target));
  }

  public static void copy(File from, File to, boolean overwrite) throws IOException {
    copy(from.toPath(), to.toPath(), overwrite);
  }

  public static void copy(Path src, Path dst, boolean overwrite) throws IOException {
    LOG.info(src + (overwrite ? " over " : " into ") + dst);

    var attrs = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (attrs.isDirectory()) {
      Files.createDirectories(dst);
    }
    else if (WIN_UNPRIVILEGED && attrs.isSymbolicLink()) {
      if (overwrite) Files.deleteIfExists(dst);
      Files.createDirectories(dst.getParent());
      Files.createSymbolicLink(dst, Files.readSymbolicLink(src));
    }
    else {
      Files.createDirectories(dst.getParent());
      Files.copy(src, dst, overwrite ? COPY_REPLACE : COPY_STANDARD);
    }
  }

  public static void copyDirectory(Path from, Path to) throws IOException {
    LOG.info(from + " into " + to);

    Files.walkFileTree(from, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir != from || !Files.exists(to)) {
          Path copy = to.resolve(from.relativize(dir));
          LOG.info("  " + dir + " into " + copy);
          Files.createDirectory(copy);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path copy = to.resolve(from.relativize(file));
        LOG.info("  " + file + " into " + copy);
        if (WIN_UNPRIVILEGED && attrs.isSymbolicLink()) {
          Files.createSymbolicLink(copy, Files.readSymbolicLink(file));
        }
        else {
          Files.copy(file, copy, COPY_STANDARD);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void move(Path src, Path dst, boolean overwrite) throws IOException {
    Files.createDirectories(dst.getParent());
    Files.move(src, dst, overwrite ? MOVE_REPLACE : MOVE_STANDARD);
  }

  public static void writeString(Path file, String data) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, data);
  }

  public static void copyFileToStream(File from, OutputStream out) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(from))) {
      copyStream(in, out);
    }
  }

  public static void copyStreamToFile(InputStream from, File to) throws IOException {
    File directory = to.getParentFile();
    if (!(directory.isDirectory() || directory.mkdirs())) {
      throw new IOException("Cannot create: " + directory);
    }
    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
      copyStream(from, out);
    }
  }

  public static byte[] readBytes(InputStream in) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    copyStream(in, byteOut);
    return byteOut.toByteArray();
  }

  public static byte[] readBytes(InputStream in, int count) throws IOException {
    byte[] bytes = new byte[count];
    int offset = 0;
    while (offset < count) {
      int n = in.read(bytes, offset, count - offset);
      if (n < 0) throw new IOException("A premature end of stream");
      offset += n;
    }
    return bytes;
  }

  public static void writeBytes(byte[] from, int length, OutputStream to) throws IOException {
    int offset = 0;
    while (offset < length) {
      int chunkSize = Math.min(BUFFER_SIZE, length - offset);
      to.write(from, offset, chunkSize);
      offset += chunkSize;
    }
  }

  public static void copyStream(InputStream from, OutputStream to) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    while (true) {
      int read = from.read(buffer);
      if (read < 0) break;
      to.write(buffer, 0, read);
    }
  }

  public static InputStream getEntryInputStream(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = getZipEntry(zipFile, entryPath);
    return findEntryInputStreamForEntry(zipFile, entry);
  }

  public static InputStream findEntryInputStream(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = zipFile.getEntry(entryPath);
    return entry != null ? findEntryInputStreamForEntry(zipFile, entry) : null;
  }

  public static ZipEntry getZipEntry(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = zipFile.getEntry(entryPath);
    if (entry == null) throw new FileNotFoundException("Entry " + entryPath + " not found");
    LOG.info("entryPath: " + entryPath);
    return entry;
  }

  public static InputStream findEntryInputStreamForEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
    if (entry.isDirectory()) return null;
    // There is a bug in some JVM implementations where for a directory "X/" in a .zip file, if we do
    // "zip.getEntry("X/").isDirectory()" returns true, but if we do "zip.getEntry("X").isDirectory()" is false.
    // getEntry for "name" falls back to finding "X/", so here we make sure this didn't happen.
    if (zipFile.getEntry(entry.getName() + "/") != null) return null;

    return new BufferedInputStream(zipFile.getInputStream(entry));
  }

  // always collect files and folders - to avoid cases such as IDEA-152249
  public static LinkedHashSet<String> collectRelativePaths(Path root) throws IOException {
    LinkedHashSet<String> result = new LinkedHashSet<>();

    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (dir != root) {
          result.add(root.relativize(dir).toString().replace('\\', '/') + '/');
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        result.add(root.relativize(file).toString().replace('\\', '/'));
        return FileVisitResult.CONTINUE;
      }
    });

    return result;
  }

  public static InputStream newFileInputStream(File file) throws IOException {
    return new FileInputStream(file);
  }

  public static class OpenByteArrayOutputStream extends ByteArrayOutputStream {
    @Override
    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    public synchronized void writeTo(OutputStream out) throws IOException {
      Utils.writeBytes(buf, count, out);
    }
  }

  public static void pause(long millis) {
    try { Thread.sleep(millis); }
    catch (InterruptedException ignore) { }
  }
}
