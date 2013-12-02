package com.intellij.updater;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
  // keep buffer static as there may be many calls of the copyStream method.
  private static final byte[] BUFFER = new byte[64 * 1024];
  private static File myTempDir;

  public static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar");
  }

  @SuppressWarnings({"SSBasedInspection"})
  public static File createTempFile() throws IOException {
    if (myTempDir == null) {
      myTempDir = File.createTempFile("idea.updater.", ".tmp");
      delete(myTempDir);
      myTempDir.mkdirs();
    }

    return File.createTempFile("temp.", ".tmp", myTempDir);
  }

  public static File createTempDir() throws IOException {
    File result = createTempFile();
    delete(result);
    result.mkdirs();
    return result;
  }

  public static void cleanup() throws IOException {
    if (myTempDir == null) return;
    delete(myTempDir);
    myTempDir = null;
  }

  public static void delete(File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File each : files) {
          delete(each);
        }
      }
    }
    for (int i = 0; i < 10; i++) {
      if (file.delete() || !file.exists()) return;
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignore) {
      }
    }
    if (file.exists()) throw new IOException("Cannot delete file " + file);
  }

  public static void setExecutable(File file, boolean executable) throws IOException {
    if (executable && !file.setExecutable(true)) {
      throw new IOException("Cannot set executable permissions for: " + file);
    }
  }

  public static void copy(File from, File to) throws IOException {
    if (from.isDirectory()) {
      File[] files = from.listFiles();
      if (files == null) throw new IOException("Cannot get directory's content: " + from);
      for (File each : files) {
        copy(each, new File(to, each.getName()));
      }
    }
    else {
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
    InputStream result = findEntryInputStream(zipFile, entryPath);
    if (result == null) throw new IOException("Entry " + entryPath + " not found");
    return result;
  }

  public static InputStream findEntryInputStream(ZipFile zipFile, String entryPath) throws IOException {
    ZipEntry entry = zipFile.getEntry(entryPath);
    if (entry == null || entry.isDirectory()) return null;

    // if isDirectory check failed, check presence of 'file/' manually
    if (!entryPath.endsWith("/") && zipFile.getEntry(entryPath + "/") != null) return null;

    return new BufferedInputStream(zipFile.getInputStream(entry));
  }

  public static LinkedHashSet<String> collectRelativePaths(File dir) {
    LinkedHashSet<String> result = new LinkedHashSet<String>();
    collectRelativePaths(dir, result, null);
    return result;
  }

  private static void collectRelativePaths(File dir, LinkedHashSet<String> result, String parentPath) {
    File[] children = dir.listFiles();
    if (children == null) return;

    for (File each : children) {
      String relativePath = (parentPath == null ? "" : parentPath + "/") + each.getName();
      if (each.isDirectory()) {
        collectRelativePaths(each, result, relativePath);
      }
      else {
        result.add(relativePath);
      }
    }
  }
}
