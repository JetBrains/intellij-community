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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
  private static final long REQUIRED_FREE_SPACE = 10_0000_0000L;

  private static final int BUFFER_SIZE = 8192;  // to minimize native memory allocations for I/O operations
  private static final byte[] BUFFER = new byte[BUFFER_SIZE];

  private static File myTempDir;

  public static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar");
  }

  private static File findUniqueName(String path) {
    int index = 0;
    File myTempFile;
    do {
      myTempFile = new File(path + ".tmp." + index++);
    }
    while (myTempFile.exists());
    return myTempFile;
  }

  public static File getTempFile(String name) throws IOException {
    if (myTempDir == null) {
      myTempDir = findUniqueName(Runner.getDir(REQUIRED_FREE_SPACE) + "/idea.updater.files");
      if (!myTempDir.mkdirs()) throw new IOException("Cannot create working directory: " + myTempDir);
      Runner.logger().info("created working directory: " + myTempDir);
    }
    return findUniqueName(myTempDir.getPath() + '/' + name);
  }

  public static File createTempDir() throws IOException {
    File tempDir = getTempFile("temp");
    if (!tempDir.mkdir()) throw new IOException("Cannot create temp directory: " + tempDir);
    Runner.logger().info("created temp directory: " + tempDir.getPath());
    return tempDir;
  }

  public static void cleanup() throws IOException {
    if (myTempDir == null) return;
    delete(myTempDir);
    Runner.logger().info("deleted working directory: " + myTempDir.getPath());
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

  @SuppressWarnings("BusyWait")
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

      try { Thread.sleep(10); }
      catch (InterruptedException ignore) { }
    }

    throw new IOException("Cannot delete: " + path);
  }

  public static boolean isExecutable(File file) {
    return file.canExecute();
  }

  public static void setExecutable(File file) throws IOException {
    Runner.logger().info("Setting executable permissions for: " + file);
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
    Files.createSymbolicLink(path, Paths.get(target));
  }

  public static void copy(File from, File to) throws IOException {
    if (!from.exists()) throw new IOException("Source does not exist: " + from);

    if (isLink(from)) {
      if (to.exists()) throw new IOException("Target already exists: " + to);
      Runner.logger().info("Link: " + from.getPath() + " to " + to.getPath());

      File dir = to.getParentFile();
      if (!(dir.isDirectory() || dir.mkdirs())) throw new IOException("Cannot create: " + dir);

      createLink(readLink(from), to);
    }
    else if (from.isDirectory()) {
      Runner.logger().info("Dir: " + from.getPath() + " to " + to.getPath());
      if (!(to.mkdirs() || to.isDirectory())) throw new IOException("Cannot create: " + to);
    }
    else {
      if (to.exists()) throw new IOException("Target already exists: " + to);
      Runner.logger().info("File: " + from.getPath() + " to " + to.getPath());

      File dir = to.getParentFile();
      if (!(dir.isDirectory() || dir.mkdirs())) throw new IOException("Cannot create: " + dir);

      try (InputStream in = new BufferedInputStream(new FileInputStream(from))) {
        copyStreamToFile(in, to);
      }

      if (isExecutable(from)) {
        setExecutable(to);
      }
    }
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
      if (n < 0) throw new IOException("Premature end of stream");
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
    // getEntry for "name" falls back to finding "X/", so here we make sure that didn't happen.
    if (zipFile.getEntry(entry.getName() + "/") != null) return null;

    return new BufferedInputStream(zipFile.getInputStream(entry));
  }

  public static LinkedHashSet<String> collectRelativePaths(File dir) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    collectRelativePaths(dir, result, null);
    return result;
  }

  private static void collectRelativePaths(File dir, LinkedHashSet<String> result, String parentPath) {
    File[] children = dir.listFiles();
    if (children == null) return;

    for (File each : children) {
      String relativePath = (parentPath == null ? "" : parentPath + '/') + each.getName();
      if (each.isDirectory()) {
        result.add(relativePath + '/');  // the trailing slash is used by zip to determine whether it is a directory
        collectRelativePaths(each, result, relativePath);
      }
      else {
        result.add(relativePath);
      }
    }
  }

  public static InputStream newFileInputStream(File file, boolean normalize) throws IOException {
    return normalize && isZipFile(file.getName()) ? new NormalizedZipInputStream(file) : new FileInputStream(file);
  }

  private static class NormalizedZipInputStream extends InputStream {
    private final ZipFile myZip;
    private final List<? extends ZipEntry> myEntries;
    private InputStream myStream = null;
    private int myNextEntry = 0;
    private byte[] myByte = new byte[1];

    private NormalizedZipInputStream(File file) throws IOException {
      myZip = new ZipFile(file);
      myEntries = Collections.list(myZip.entries());
      Collections.sort(myEntries, Comparator.comparing(ZipEntry::getName));
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
      writeBytes(buf, count, out);
    }
  }
}