/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.CommonBundle;
import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FileUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileUtil");
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
    InputStream stream = new FileInputStream(file);
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try{
      return loadText(reader, (int)file.length());
    }
    finally{
      reader.close();
    }
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
    final InputStream stream = new BufferedInputStream(new FileInputStream(file));
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

  @NotNull public static String loadTextAndClose(Reader reader) throws IOException {
    try {
      return new String(adaptiveLoadText(reader));
    }
    finally {
      reader.close();
    }
  }

  @NotNull public static char[] adaptiveLoadText(Reader reader) throws IOException {
    char[] chars = new char[4096];
    List<char[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = reader.read(chars, count, chars.length-count);
      if (n <= 0) break;
      count += n;
      total += n;
      if (count == chars.length) {
        if (buffers == null) {
          buffers = new ArrayList<char[]>();
        }
        buffers.add(chars);
        chars = new char[chars.length * 2];
        count = 0;
      }
    }
    char[] result = new char[total];
    if (buffers != null) {
      for (char[] buffer : buffers) {
        System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
        total -= buffer.length;
      }
    }
    System.arraycopy(chars, 0, result, result.length - total, total);
    return result;
  }

  public static byte[] adaptiveLoadBytes(InputStream stream) throws IOException{
    byte[] bytes = new byte[4096];
    List<byte[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = stream.read(bytes, count, bytes.length-count);
      if (n <= 0) break;
      count += n;
      total += n;
      if (count == bytes.length) {
        if (buffers == null) {
          buffers = new ArrayList<byte[]>();
        }
        buffers.add(bytes);
        bytes = new byte[bytes.length * 2];
        count = 0;
      }
    }
    byte[] result = new byte[total];
    if (buffers != null) {
      for (byte[] buffer : buffers) {
        System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
        total -= buffer.length;
      }
    }
    System.arraycopy(bytes, 0, result, result.length - total, total);
    return result;
  }

  public static File createTempDirectory(@NonNls String prefix, @NonNls String suffix) throws IOException{
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.mkdir();
    return file;
  }

  public static File createTempFile(@NonNls String prefix, @NonNls String suffix) throws IOException{
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
        if (++exceptionsCount >= 100) {
          throw e;
        }
      }
    }
  }

  public static String getTempDirectory() {
    return System.getProperty("java.io.tmpdir");
  }

  public static void asyncDelete(File file) {
    final File tempFile = renameToTempFile(file);
    if (tempFile == null) return;

    startDeletionThread(tempFile);
  }
  public static void asyncDelete(Collection<File> files) {
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

  private static void startDeletionThread(final File... tempFiles) {
    //noinspection HardCodedStringLiteral
    Thread t = new Thread("File deletion thread") {
      public void run() {
        ShutDownTracker.getInstance().registerStopperThread(this);
        try {
          for (File tempFile : tempFiles) {
            delete(tempFile);
          }
        }
        finally {
          ShutDownTracker.getInstance().unregisterStopperThread(this);
        }
      }
    };
    t.start();
    t.setPriority(Thread.MIN_PRIORITY);
  }

  private static File renameToTempFile(File file) {
    File parent = new File(getTempDirectory());
    final String originalFileName = file.getName();
    File tempFile = getTempFile(originalFileName, parent);

    if (!file.renameTo(tempFile)) {
      //second chance: try to move to the same directory - may fire events in case of file watcher
      parent = file.getParentFile();
      tempFile = getTempFile(originalFileName, parent);
      if (!file.renameTo(tempFile)) {
        delete(file);
        return null;
      }
    }
    return tempFile;
  }

  private static File getTempFile(String originalFileName, File parent) {
    int randomSuffix = (int)(System.currentTimeMillis() % 1000);
    for (int i = randomSuffix; ; i++) {
      @NonNls String name = "___" + originalFileName + i + ".__del__";
      File tempFile = new File(parent, name);
      if (!tempFile.exists()) return tempFile;
    }
  }

  public static boolean delete(File file){
    File[] files = file.listFiles();
    if (files != null) {
      for (File file1 : files) {
        delete(file1);
      }
    }

    for (int i = 0; i < 10; i++){
      if (file.delete() || !file.exists()) return true;
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
    FileInputStream fis = new FileInputStream(fromFile);
    FileOutputStream fos;
    try {
      fos = new FileOutputStream(toFile);
    }
    catch (FileNotFoundException e) {
      File parentFile = toFile.getParentFile();
      if (parentFile == null) {
        return; // TODO: diagnostics here
      }
      parentFile.mkdirs();
      toFile.createNewFile();
      fos = new FileOutputStream(toFile);
    }

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
        fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
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
      LOG.error(fromDir.getAbsolutePath() + " is ancestor of " + toDir + ". Can't copy to itself.");
      return;
    }
    File[] files = fromDir.listFiles();
    if(!fromDir.canRead()) throw new IOException(CommonBundle.message("exception.directory.is.not.readable", fromDir.getPath()));
    if(files == null) throw new IOException(CommonBundle.message("exception.directory.is.invalid", fromDir.getPath()));
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
    return findSequentNonexistentFile(aParentFolder, aFilePrefix, aExtension).getName();
  }

  public static File findSequentNonexistentFile(final File aParentFolder, final String aFilePrefix, final String aExtension) {
    int postfix = 0;
    String ext = 0 == aExtension.length() ? "" : "." + aExtension;

    File candidate = new File(aParentFolder, aFilePrefix + ext);
    while (candidate.exists()) {
      postfix++;
      candidate = new File(aParentFolder, aFilePrefix + Integer.toString(postfix) + ext);
    }
    return candidate;
  }

  public static String toSystemDependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  public static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('\\', '/');
  }

  //TODO: does only %20 need to be unescaped?
  public static String unquote(String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return StringUtil.replace(urlString, "%20", " ");
  }

  public static boolean isFilePathAcceptable(File file, @Nullable FileFilter fileFilter) {
    do {
      if (fileFilter != null && !fileFilter.accept(file)) return false;
      file = file.getParentFile();
    }
    while (file != null);
    return true;
  }

  public static void rename(final File source, final File target) throws IOException {
    if (source.renameTo(target)) return;

    copy(source, target);
    delete(source);
  }

  public static boolean startsWith(String path1, String path2) {
    final int length1 = path1.length();
    final int length2 = path2.length();
    if (length2 == 0) return true;
    if (length2 > length1) return false;
    if (!path1.regionMatches(!SystemInfo.isFileSystemCaseSensitive, 0, path2, 0, length2)) return false;
    if (length1 == length2) return true;
    char last2 = path2.charAt(length2 - 1);
    char next1;
    if (last2 == '/' || last2 == File.separatorChar) {
      next1 = path1.charAt(length2 -1);
    }
    else {
      next1 = path1.charAt(length2);
    }
    return next1 == '/' || next1 == File.separatorChar;
  }

  public static boolean pathsEqual(String path1, String path2) {
    return SystemInfo.isFileSystemCaseSensitive? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1).toLowerCase();
  }
}
