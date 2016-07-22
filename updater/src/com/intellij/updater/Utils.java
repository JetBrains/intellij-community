package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
  // keep buffer static as there may be many calls of the copyStream method.
  private static final byte[] BUFFER = new byte[64 * 1024];
  private static File myTempDir;

  public static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar");
  }

  public static File getTempFile(String path) throws IOException {
    int index = 0;
    File myTempFile = new File(path + ".tmp." + index++);
    while (myTempFile.exists()) {
      myTempFile = new File(path + ".tmp." + index++);
    }
    if (myTempFile.setWritable(true, false)) throw new IOException("Cannot set write permissions for dir: " + myTempFile);
    return myTempFile;
  }

  @SuppressWarnings({"SSBasedInspection"})
  public static File createTempFile() throws IOException {
    if (myTempDir == null) {
      long requiredFreeSpace = 1000000000;
      myTempDir = getTempFile(Runner.getDir(requiredFreeSpace) + "/idea.updater.files");
      delete(myTempDir);
      myTempDir.mkdirs();
      Runner.logger.info("created temp file: " + myTempDir.getPath());
    }
    return getTempFile(myTempDir + "/temp");
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
    if (!normalize || !isZipFile(file.getName())) {
      return new FileInputStream(file);
    }
    return new NormalizedZipInputStream(file);
  }

  static class NormalizedZipInputStream extends InputStream {

    private ArrayList<? extends ZipEntry> myEntries;
    private InputStream myStream = null;
    private int myNextEntry = 0;
    private final ZipFile myZip;
    private byte[] myByte = new byte[1];

    NormalizedZipInputStream(File file) throws IOException {
      myZip = new ZipFile(file);
      myEntries = Collections.list(myZip.entries());
      Collections.sort(myEntries, new Comparator<ZipEntry>() {
        @Override
        public int compare(ZipEntry a, ZipEntry b) {
          return a.getName().compareTo(b.getName());
        }
      });

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
