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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
  private static final long REQUIRED_FREE_SPACE = 10_0000_0000L;

  // keep buffer static as there may be many calls of the copyStream method.
  private static final byte[] BUFFER = new byte[64 * 1024];
  private static File myTempDir;

  public static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar");
  }

  private static File findUniqueName(String path) throws IOException {
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
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File each : files) {
          delete(each);
          Runner.logger().info("deleted file " + each.getPath());
        }
      }
    }
    for (int i = 0; i < 10; i++) {
      if (file.delete() || !file.exists()) return;
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignore) {
        Runner.printStackTrace(ignore);
      }
    }
    if (file.exists()) throw new IOException("Cannot delete file " + file);
  }

  public static void setExecutable(File file, boolean executable) throws IOException {
    if (executable && !file.setExecutable(true, false)) {
      Runner.logger().error("Can't set executable permissions for file");
      throw new IOException("Cannot set executable permissions for: " + file);
    }
  }

  public static boolean isLink(File file) throws IOException {
    return Files.isSymbolicLink(Paths.get(file.getAbsolutePath()));
  }

  public static void createLink(String target, File link) throws IOException {
    if (target == "") {
      Runner.logger().error("Can't create link for " +  link.getName());
    } else {
      if (link.exists()) {
        delete(link);
      }
      Files.createSymbolicLink(Paths.get(link.getAbsolutePath()), Paths.get(target));
    }
  }

  public static void copy(File from, File to) throws IOException {
    if (from.isDirectory()) {
      if (! to.exists()) {
        Runner.logger().info("Dir: " + from.getPath() + " to " + to.getPath());
        to.mkdirs();
        File[] files = from.listFiles();
        if (files == null) throw new IOException("Cannot get directory's content: " + from);
        for (File each : files) {
          copy(each, new File(to, each.getName()));
        }
      }
    } else {
      if (! isLink(from) && from.exists()) {
        Runner.logger().info("File: " + from.getPath() + " to " + to.getPath());
        InputStream in = new BufferedInputStream(new FileInputStream(from));
        try {
          copyStreamToFile(in, to);
        }
        finally {
          in.close();
        }
        setExecutable(to, from.canExecute());
      }
    }
  }


  public static void mirror(File from, File to) throws IOException {
    if (from.exists()) {
      copy(from, to);
    } else {
      delete(to);
    }
  }

  public static void copyFileToStream(File from, OutputStream out) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(from));
    try {
      copyStream(in, out);
    }
    finally {
      in.close();
    }
  }

  public static void copyStreamToFile(InputStream from, File to) throws IOException {
    to.getParentFile().mkdirs();
    OutputStream out = new BufferedOutputStream(new FileOutputStream(to));
    try {
      copyStream(from, out);
    }
    finally {
      out.close();
    }
  }

  public static void copyBytesToStream(ByteArrayOutputStream from, OutputStream to) throws IOException {
    OutputStream out = new BufferedOutputStream(to);
    try {
      from.writeTo(out);
    }
    finally {
      out.flush();
    }
  }

  public static void copyBytesToStream(byte[] bytes, OutputStream to) throws IOException {
    to.write(bytes);
  }

  public static byte[] readBytes(InputStream in) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    try {
      copyStream(in, byteOut);
    }
    finally {
      byteOut.close();
    }
    return byteOut.toByteArray();
  }

  public static void copyStream(InputStream in, OutputStream out) throws IOException {
    while (true) {
      int read = in.read(BUFFER);
      if (read < 0) break;
      out.write(BUFFER, 0, read);
    }
  }

  public static InputStream getEntryInputStream(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = getZipEntry(zipFile, entryPath);
    return findEntryInputStreamForEntry(zipFile, entry);
  }

  public static InputStream findEntryInputStream(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = zipFile.getEntry(entryPath);
    if (entry == null) return null;
    return findEntryInputStreamForEntry(zipFile, entry);
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

  public static LinkedHashSet<String> collectRelativePaths(File dir, boolean includeDirectories) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    collectRelativePaths(dir, result, null, includeDirectories);
    return result;
  }

  private static void collectRelativePaths(File dir, LinkedHashSet<String> result, String parentPath, boolean includeDirectories) {
    File[] children = dir.listFiles();
    if (children == null) return;

    for (File each : children) {
      String relativePath = (parentPath == null ? "" : parentPath + "/") + each.getName();
      if (each.isDirectory()) {
        if (includeDirectories) {
          // The trailing slash is very important, as it's used by zip to determine whether it is a directory.
          result.add(relativePath + "/");
        }
        collectRelativePaths(each, result, relativePath, includeDirectories);
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
    private List<? extends ZipEntry> myEntries;
    private InputStream myStream = null;
    private int myNextEntry = 0;
    private final ZipFile myZip;
    private byte[] myByte = new byte[1];

    NormalizedZipInputStream(File file) throws IOException {
      myZip = new ZipFile(file);
      myEntries = Collections.list(myZip.entries());
      Collections.sort(myEntries, (Comparator<ZipEntry>)(a, b) -> a.getName().compareTo(b.getName()));

      loadNextEntry();
    }

    private void loadNextEntry() throws IOException {
      if (myStream != null) {
        myStream.close();
      }
      myStream = null;
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
}