package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
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
      long requiredFreeSpace = 1000000000;
      // Create a (regular) file using the createTempFile api. This is used as a marker to indicate the existence of the
      // corresponding directory, since there's no atomic API that guarantees creation of new directories.
      File tempFileBase = File.createTempFile("idea.updater.files", "marker", new File(Runner.getDir(requiredFreeSpace)));
      myTempDir = new File(tempFileBase.getPath().replaceAll("marker$", ""));
      delete(myTempDir);
      myTempDir.mkdirs();
      Runner.logger.info("created temp file: " + myTempDir.getPath());
    }
    return File.createTempFile("temp", null, myTempDir);
  }

  public static File createTempDir() throws IOException {
    File result = createTempFile();
    delete(result);
    Runner.logger.info("deleted tmp dir: " + result.getPath());
    result.mkdirs();
    Runner.logger.info("created tmp dir: " + result.getPath());
    if (! result.exists()) throw new IOException("Cannot create temp dir: " + result);
    return result;
  }

  public static void cleanup() throws IOException {
    if (myTempDir == null) return;
    delete(myTempDir);
    Runner.logger.info("deleted file " + myTempDir.getPath());
    myTempDir = null;
  }

  public static void delete(File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File each : files) {
          delete(each);
          Runner.logger.info("deleted file " + each.getPath());
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
      Runner.logger.error("Can't set executable permissions for file");
      throw new IOException("Cannot set executable permissions for: " + file);
    }
  }

  public static void copy(File from, File to) throws IOException {
    Runner.logger.info("from " + from.getPath() + " to " + to.getPath());
    if (from.isDirectory()) {
      to.mkdirs();
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
    Runner.logger.info("entryPath: " + entryPath);
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
    LinkedHashSet<String> result = new LinkedHashSet<String>();
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

  protected static boolean isSymlink(File file) throws IOException {
    return !file.getAbsolutePath().equalsIgnoreCase(file.getCanonicalPath());
  }

  protected static String getSymlinkTarget(File file) throws IOException {
    String target = file.getCanonicalPath();
    String link = file.getAbsolutePath();
    String[] segments1 = link.split(Pattern.quote(File.separator));
    String[] segments2 = target.split(Pattern.quote(File.separator));

    int len1 = segments1.length;
    int len2 = segments2.length;
    int len = Math.min(len1, len2);
    int start = 0;
    for (; start < len; start++) {
      if (!segments1[start].equals(segments2[start])) {
        break;
      }
    }

    StringBuilder result = new StringBuilder();
    for (int i = start; i < len1 - 1; i++) {
      result.append("..").append(File.separator);
    }
    while (start < len2) {
      result.append(segments2[start]);
      if (++start < len2) {
        result.append(File.separator);
      }
    }

    return result.toString();
  }
}
