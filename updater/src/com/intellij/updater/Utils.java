// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
  private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
  public static final boolean IS_WINDOWS = OS_NAME.startsWith("windows");
  public static final boolean IS_MAC = OS_NAME.startsWith("mac");

  private static final long REQUIRED_FREE_SPACE = 2_000_000_000L;

  private static final int BUFFER_SIZE = 8192;  // to minimize native memory allocations for I/O operations
  private static final byte[] BUFFER = new byte[BUFFER_SIZE];

  private static File myTempDir;

  public static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar");
  }

  public static String findDirectory(long requiredFreeSpace) {
    String dir = System.getProperty("idea.updater.log");
    if (dir == null || !isValidDir(dir, requiredFreeSpace)) {
      dir = System.getProperty("java.io.tmpdir");
      if (!isValidDir(dir, requiredFreeSpace)) {
        dir = System.getProperty("user.home");
      }
    }
    return dir;
  }

  private static boolean isValidDir(String path, long space) {
    File dir = new File(path);
    return dir.isDirectory() && dir.canWrite() && dir.getUsableSpace() >= space;
  }

  public static File getTempFile(String name) throws IOException {
    if (myTempDir == null) {
      myTempDir = Files.createTempDirectory(Paths.get(findDirectory(REQUIRED_FREE_SPACE)), "idea.updater.files.").toFile();
      Runner.logger().info("created a working directory: " + myTempDir);
    }

    File myTempFile;
    int index = 0;
    do {
      myTempFile = new File(myTempDir, name + ".tmp." + index++);
    }
    while (myTempFile.exists());
    return myTempFile;
  }

  public static void cleanup() throws IOException {
    if (myTempDir == null) return;
    delete(myTempDir);
    Runner.logger().info("deleted a working directory: " + myTempDir.getPath());
    myTempDir = null;
  }

  public static void delete(File file) throws IOException {
    Path start = file.toPath();
    if (Files.exists(start, LinkOption.NOFOLLOW_LINKS)) {
      Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
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
    for (int i = 0; i < 10; i++) {
      try {
        if (Files.deleteIfExists(path) || !Files.exists(path)) {
          Runner.logger().info("deleted: " + path);
          return;
        }
      }
      catch (AccessDeniedException e) {
        try {
          DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class);
          if (view != null && view.readAttributes().isReadOnly()) {
            view.setReadOnly(false);
            continue;
          }
        }
        catch (IOException ignore) { }
      }
      catch (IOException ignore) { }

      pause(10);
    }

    throw new IOException("Cannot delete: " + path);
  }

  public static boolean isExecutable(File file) {
    return file.canExecute();
  }

  public static void setExecutable(File file) throws IOException {
    setExecutable(file, true);
  }

  public static void setExecutable(File file, boolean executable) throws IOException {
    Runner.logger().info("Setting executable permissions for: " + file);
    if (!file.setExecutable(executable, false)) {
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
    Files.createSymbolicLink(path, Paths.get(target));
  }

  public static void copy(File from, File to, boolean overwrite) throws IOException {
    Runner.logger().info(from + (overwrite ? " over " : " into ") + to);

    if (Files.isDirectory(from.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      Files.createDirectories(to.toPath());
    }
    else {
      Files.createDirectories(to.toPath().getParent());
      CopyOption[] options =
        overwrite ? new CopyOption[]{LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING} :
                    new CopyOption[]{LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES};
      Files.copy(from.toPath(), to.toPath(), options);
    }
  }

  public static void copyDirectory(Path from, Path to) throws IOException {
    Runner.logger().info(from + " into " + to);

    CopyOption[] options = {LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES};
    Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir != from || !Files.exists(to)) {
          Path copy = to.resolve(from.relativize(dir));
          Runner.logger().info("  " + dir + " into " + copy);
          Files.createDirectory(copy);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path copy = to.resolve(from.relativize(file));
        Runner.logger().info("  " + file + " into " + copy);
        Files.copy(file, copy, options);
        return FileVisitResult.CONTINUE;
      }
    });
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
    while (true) {
      int read = from.read(BUFFER);
      if (read < 0) break;
      to.write(BUFFER, 0, read);
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
    if (entry == null) throw new IOException("Entry " + entryPath + " not found");
    Runner.logger().info("entryPath: " + entryPath);
    return entry;
  }

  public static InputStream findEntryInputStreamForEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
    if (entry.isDirectory()) return null;
    // There is a bug in some JVM implementations where for a directory "X/" in a zipfile, if we do
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

  public static InputStream newFileInputStream(File file, boolean normalize) throws IOException {
    return normalize && isZipFile(file.getName()) ? new NormalizedZipInputStream(file) : new FileInputStream(file);
  }

  private static class NormalizedZipInputStream extends InputStream {
    private final ZipFile myZip;
    private final List<? extends ZipEntry> myEntries;
    private InputStream myStream = null;
    private int myNextEntry = 0;
    private final byte[] myByte = new byte[1];

    private NormalizedZipInputStream(File file) throws IOException {
      myZip = new ZipFile(file);
      myEntries = Collections.list(myZip.entries());
      myEntries.sort(Comparator.comparing(ZipEntry::getName));
      loadNextEntry();
    }

    private void loadNextEntry() throws IOException {
      if (myStream != null) {
        myStream.close();
        myStream = null;
      }
      while (myNextEntry < myEntries.size() && myStream == null) {
        myStream = findEntryInputStreamForEntry(myZip, myEntries.get(myNextEntry++));
      }
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
      if (myStream == null) {
        return -1;
      }
      int b = myStream.read(bytes, off, len);
      if (b == -1) {
        loadNextEntry();
        return read(bytes, off, len);
      }
      return b;
    }

    @Override
    public int read() throws IOException {
      int b = read(myByte, 0, 1);
      return b == -1 ? -1 : myByte[0];
    }

    @Override
    public void close() throws IOException {
      if (myStream != null) {
        myStream.close();
      }
      myZip.close();
    }
  }

  public static class OpenByteArrayOutputStream extends ByteArrayOutputStream {
    @Override
    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    public synchronized void writeTo(OutputStream out) throws IOException {
      //noinspection UnnecessarilyQualifiedStaticUsage
      Utils.writeBytes(buf, count, out);
    }
  }

  public static void pause(long millis) {
    try { Thread.sleep(millis); }
    catch (InterruptedException ignore) { }
  }
}