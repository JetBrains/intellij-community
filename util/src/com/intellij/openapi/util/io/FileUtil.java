/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.io;

import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
  private static final byte[] BUFFER = new byte[1024 * 20];

  public static String getRelativePath(File base, File file) {
    if (base == null || file == null) return null;

    if (!base.isDirectory()) {
      base = base.getParentFile();
      if (base == null) return null;
    }

    if (base.equals(file)) return ".";

    String basePath = base.getAbsolutePath();
    if (!basePath.endsWith(File.separator)) basePath += File.separatorChar;
    final String filePath = file.getAbsolutePath();

    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abcde/base" and file="/temp/ab"
    while (len < filePath.length() && len < basePath.length() && filePath.charAt(len) == basePath.charAt(len)) {
      if (basePath.charAt(len) == File.separatorChar) {
        lastSeparatorIndex = len;
      }
      len++;
    }

    if (len == 0) return null;

    StringBuffer relativePath = new StringBuffer();
    for (int i=len; i < basePath.length(); i++) {
      if (basePath.charAt(i) == File.separatorChar) {
        relativePath.append("..");
        relativePath.append(File.separatorChar);
      }
    }
    relativePath.append(filePath.substring(lastSeparatorIndex + 1));

    return relativePath.toString();
  }

  public static boolean isAncestor(File ancestor, File file, boolean strict) throws IOException {
    ancestor = ancestor.getCanonicalFile();
    File parent = strict ? file.getCanonicalFile().getParentFile() : file.getCanonicalFile();
    while (true) {
      if (parent == null) {
        return false;
      }
      if (parent.equals(ancestor)) {
        return true;
      }
      parent = parent.getParentFile();
    }
  }

  public static char[] loadFileText(File file) throws IOException {
    return loadFileText(file, null);
  }

  public static char[] loadFileText(File file, String encoding) throws IOException{
    FileInputStream stream = new FileInputStream(file);
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    char[] chars;
    try{
      chars = loadText(reader, (int)file.length());
    }
    finally{
      reader.close();
    }
    return chars;
  }

  public static char[] loadText(Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length){
      return chars;
    }
    else{
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  public static byte[] loadFileBytes(File file) throws IOException {
    InputStream stream = new FileInputStream(file);
    byte[] bytes;
    try{
      bytes = loadBytes(stream, (int)file.length());
    }
    finally{
      stream.close();
    }
    return bytes;
  }

  public static byte[] loadBytes(InputStream stream, int length) throws IOException{
    byte[] bytes = new byte[length];
    int count = 0;
    while(count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  public static File createTempDirectory(String prefix, String suffix) throws IOException{
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.mkdir();
    return file;
  }

  public static File createTempFile(String prefix, String suffix) throws IOException{
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.createNewFile();
    return file;
  }

  private static File doCreateTempFile(String prefix, String suffix) throws IOException {
    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }

    int exceptionsCount = 0;
    while(true){
      try{
        return File.createTempFile(prefix, suffix).getCanonicalFile();
      }
      catch(IOException e){ // Win32 createFileExclusively access denied
        if (++exceptionsCount < 100){
          continue;
        }
        else{
          throw e;
        }
      }
    }
  }

  public static String getTempDirectory() throws IOException {
    final File file = File.createTempFile("idea", "idea");
    File parent = file.getParentFile();
    file.delete();
    return parent.getAbsolutePath();
  }

  public static void asyncDelete(File file) {
    final File tempFile = renameToTempFile(file);
    if (tempFile == null) return;

    startDeletionThread(new File[]{tempFile});
  }
  public static void asyncDelete(List<File> files) {
    List<File> tempFiles = new ArrayList<File>();
    for (File file : files) {
      final File tempFile = renameToTempFile(file);
      if (tempFile != null) {
        tempFiles.add(tempFile);
      }
    }
    if (tempFiles.size() != 0) {
      startDeletionThread(tempFiles.toArray(new File[tempFiles.size()]));
    }
  }

  private static void startDeletionThread(final File[] tempFiles) {
    Thread t = new Thread("File deletion thread") {
      public void run() {
        ShutDownTracker.getInstance().registerStopperThread(this);
        try {
          for (int i = 0; i < tempFiles.length; i++) {
            File tempFile = tempFiles[i];
            delete(tempFile);
          }
        }
        finally {
          ShutDownTracker.getInstance().unregisterStopperThread(this);
        }
      }
    };
    t.start();
  }

  private static File renameToTempFile(File file) {
    if (!file.exists()) return null;

    File parent = file.getParentFile();

    File tempFile;
    for (int i = 0; ; i++) {
      String name = "___" + file.getName() + i + ".__del__";
      tempFile = new File(parent, name);
      if (!tempFile.exists()) break;
    }

    if (!file.renameTo(tempFile)) {
      delete(file);
      return null;
    }
    return tempFile;
  }

  public static boolean delete(File file){
    if (file.isDirectory()){
      File[] files = file.listFiles();
      if (files != null) {
        for (File file1 : files) {
          delete(file1);
        }
      }
    }

    for (int i = 0; i < 10; i++){
      if (file.delete()) return true;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {

      }
    }
    return false;
  }

  public static boolean createParentDirs(File file) {
    if (!file.exists()) {
      String parentDirPath = file.getParent();
      if (parentDirPath != null) {
        return new File(parentDirPath).mkdirs();
      }
    }
    return false;
  }

  public static void copy(File fromFile, File toFile) throws IOException {
    if (!toFile.exists()) {
      File parentFile = toFile.getParentFile();
      if (parentFile == null) {
        return; // TODO: diagnostics here
      }
      parentFile.mkdirs();
      toFile.createNewFile();
    }

    FileInputStream fis = new FileInputStream(fromFile);
    FileOutputStream fos = new FileOutputStream(toFile);

    if (Patches.FILE_CHANNEL_TRANSFER_BROKEN) {
      try {
        copy(fis, fos);
      }
      finally {
        fis.close();
        fos.close();
      }
    }
    else {
      FileChannel fromChannel = fis.getChannel();
      FileChannel toChannel = fos.getChannel();

      try {
        fromChannel.transferTo(0, fromFile.length(), toChannel);
      }
      finally {
        fromChannel.close();
        toChannel.close();
      }
    }

    toFile.setLastModified(fromFile.lastModified());
  }

  public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    synchronized (BUFFER) {
      while (true) {
        int read = inputStream.read(BUFFER);
        if (read < 0) break;
        outputStream.write(BUFFER, 0, read);
      }
    }
  }

  public static void copyDir(File fromDir, File toDir) throws IOException {
    toDir.mkdirs();
    if (isAncestor(fromDir, toDir, true)) {
      Logger.getInstance("#com.intellij.openapi.util.io.FileUtil").error(fromDir.getAbsolutePath() + " is ancestor of " + toDir + ". Can't copy to itself.");
      return;
    }
    File[] files = fromDir.listFiles();
    if(files == null) throw new IOException("Directory is invalid " + fromDir.getPath());
    for (File file : files) {
      if (file.isDirectory()) {
        copyDir(file, new File(toDir, file.getName()));
      }
      else {
        copy(file, new File(toDir, file.getName()));
      }
    }
  }

  public static String getNameWithoutExtension(File file) {
    String name = file.getName();
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }
  public static String createSequentFileName(File aParentFolder, String aFilePrefix, String aExtension) {
    int postfix = 0;
    String ext = 0 == aExtension.length() ? "" : "." + aExtension;

    File candidate = new File(aParentFolder, aFilePrefix + Integer.toString(postfix) + ext);
    while (candidate.exists()) {
      postfix++;
      candidate = new File(aParentFolder, aFilePrefix + Integer.toString(postfix) + ext);
    }

    return candidate.getName();
  }

  public static String toSystemDependentName(String aFileName) {
    return aFileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  public static String toSystemIndependentName(String aFileName) {
    return aFileName.replace('\\', '/');
  }

  //TODO: does only %20 need to be unescaped?
  public static String unquote(String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return StringUtil.replace(urlString, "%20", " ");
  }

  public static boolean isFilePathAcceptable(File file, FileFilter fileFilter) {
    do {
      if (!fileFilter.accept(file)) return false;
      file = file.getParentFile();
    }
    while (file != null);
    return true;
  }

  public static void rename(final File source, final File target) throws IOException {
    copy(source, target);
    delete(source);
  }

  public static boolean startsWith(String path1, String path2) {
    if (path2.length() > path1.length()) {
      return false;
    }
    return path1.regionMatches(!SystemInfo.isFileSystemCaseSensitive, 0, path2, 0, path2.length());
  }

  public static boolean pathsEqual(String path1, String path2) {
    return SystemInfo.isFileSystemCaseSensitive? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  public static interface FileVisitor {
    int PROCEED = 0;
    int STOP = 1;
    int DO_NOT_RECURSE = 2;
    // visitor should return one of: PROCEED, STOP, DO_NOT_RECURSE
    int accept(File file);
  }

  public static int visitFilesRecursively(File rootDir, FileVisitor fileVisitor) {
    final File[] files = rootDir.listFiles();
    if (files != null) {
      for (File file : files) {
        int res = fileVisitor.accept(file);
        if (res == FileVisitor.PROCEED && file.isDirectory()) {
          res = visitFilesRecursively(file, fileVisitor);
        }
        if (res == FileVisitor.STOP) return res;
      }
    }
    return FileVisitor.PROCEED;
  }
}