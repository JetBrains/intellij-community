/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ZipUtil");

  private ZipUtil() {}


  /*
   * Adds a new file entry to the ZIP output stream.
   */
  public static boolean addFileToZip(ZipOutputStream zos,
                                     File file,
                                     String relativeName,
                                     Set<String> writtenItemRelativePaths,
                                     FileFilter fileFilter) throws IOException {
    while (relativeName.length() != 0 && relativeName.charAt(0) == '/') {
      relativeName = relativeName.substring(1);
    }

    boolean isDir = file.isDirectory();
    if (isDir && !StringUtil.endsWithChar(relativeName, '/')) {
      relativeName += "/";
    }
    if (fileFilter != null && !FileUtil.isFilePathAcceptable(file, fileFilter)) return false;
    if (writtenItemRelativePaths != null && !writtenItemRelativePaths.add(relativeName)) return false;

    LOG.debug("Add "+file+" as "+relativeName);

    long size = isDir ? 0 : file.length();
    ZipEntry e = new ZipEntry(relativeName);
    e.setTime(file.lastModified());
    if (size == 0) {
      e.setMethod(ZipEntry.STORED);
      e.setSize(0);
      e.setCrc(0);
    }
    zos.putNextEntry(e);
    if (!isDir) {
      byte[] buf = new byte[1024];
      int len;
      InputStream is = new BufferedInputStream(new FileInputStream(file));
      while ((len = is.read(buf, 0, buf.length)) != -1) {
        zos.write(buf, 0, len);
      }
      is.close();
    }
    zos.closeEntry();
    return true;
  }

  public static boolean addFileOrDirRecursively(final ZipOutputStream jarOutputStream,
                                                File jarFile,
                                                File file,
                                                String relativePath,
                                                FileFilter fileFilter,
                                                Set<String> writtenItemRelativePaths) throws IOException {
    if (file.isDirectory()) {
      return addDirToZipRecursively(jarOutputStream, jarFile, file, relativePath, fileFilter, writtenItemRelativePaths);
    }
    addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, fileFilter);
    return true;
  }

  public static boolean addDirToZipRecursively(ZipOutputStream outputStream,
                                               File jarFile, File dir,
                                               String relativePath,
                                               FileFilter fileFilter,
                                               Set<String> writtenItemRelativePaths) throws IOException {
    if (FileUtil.isAncestor(dir, jarFile, false)) {
      return false;
    }
    if (!relativePath.equals("")) {
      addFileToZip(outputStream, dir, relativePath, writtenItemRelativePaths, fileFilter);
    }
    final File[] children = dir.listFiles();
    if (children != null) {
      for (int i = 0; i < children.length; i++) {
        File child = children[i];
        final String childRelativePath = (relativePath.equals("") ? "" : relativePath + "/") + child.getName();
        addFileOrDirRecursively(outputStream, jarFile, child, childRelativePath, fileFilter, writtenItemRelativePaths);
      }
    }
    return true;
  }

  public static void extract(File file, File outputDir, FilenameFilter filenameFilter) throws IOException {
    final ZipFile zipFile = new ZipFile(file);
    extract(zipFile, outputDir, filenameFilter);
    zipFile.close();
  }

  public static void extract(final ZipFile zipFile,
                             File outputDir,
                             FilenameFilter filenameFilter) throws IOException {
    final Enumeration entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = (ZipEntry)entries.nextElement();
      final File file = new File(outputDir, entry.getName());
      if (filenameFilter == null || filenameFilter.accept(file.getParentFile(), file.getName())) {
        extractEntry(entry, zipFile.getInputStream(entry), outputDir);
      }
    }
  }

  public static void extractEntry(ZipEntry entry, final InputStream inputStream, File outputDir) throws IOException {
    final boolean isDirectory = entry.isDirectory();
    final String relativeName = entry.getName();
    final File file = new File(outputDir, relativeName);
    FileUtil.createParentDirs(file);
    if (isDirectory) {
      file.mkdir();
    }
    else {
      final BufferedInputStream is = new BufferedInputStream(inputStream);
      final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(file));
      byte[] buf = new byte[1024];
      int len;
      while ((len = is.read(buf, 0, buf.length)) != -1) {
        os.write(buf, 0, len);
      }
      os.close();
      is.close();
    }
  }

  public static boolean isZipContainsFolder(File zip) throws IOException {
    ZipFile zipFile = new ZipFile(zip);
    Enumeration en = zipFile.entries();

    while (en.hasMoreElements()) {
      ZipEntry zipEntry = (ZipEntry)en.nextElement();

      if (zipEntry.isDirectory()) {
        return true;
      }
    }
    zipFile.close();
    return false;
  }

  public static boolean isZipContainsEntry(File zip, String relativePath) throws IOException {
    ZipFile zipFile = new ZipFile(zip);
    Enumeration en = zipFile.entries();

    while (en.hasMoreElements()) {
      ZipEntry zipEntry = (ZipEntry)en.nextElement();
      if (relativePath.equals(zipEntry.getName())) {
        return true;
      }
    }
    zipFile.close();
    return false;
  }

  /*
   * update an existing jar file. Adds/replace files specified in relpathToFile map
   */
  public static void update(InputStream in, OutputStream out, Map<String, File> relpathToFile) throws IOException {
    ZipInputStream zis = new ZipInputStream(in);
    ZipOutputStream zos = new ZipOutputStream(out);
    ZipEntry e;
    byte[] buf = new byte[1024];
    int n;

    // put the old entries first, replace if necessary
    while ((e = zis.getNextEntry()) != null) {
      String name = e.getName();

      if (!relpathToFile.containsKey(name)) { // copy the old stuff
        // do our own compression
        ZipEntry e2 = new ZipEntry(name);
        e2.setMethod(e.getMethod());
        e2.setTime(e.getTime());
        e2.setComment(e.getComment());
        e2.setExtra(e.getExtra());
        if (e.getMethod() == ZipEntry.STORED) {
          e2.setSize(e.getSize());
          e2.setCrc(e.getCrc());
        }
        zos.putNextEntry(e2);
        while ((n = zis.read(buf, 0, buf.length)) != -1) {
          zos.write(buf, 0, n);
        }
      }
      else { // replace with the new files
        final File file = relpathToFile.get(name);
        //addFile(file, name, zos);
        relpathToFile.remove(name);
        addFileToZip(zos, file, name, null, null);
      }
    }

    // add the remaining new files
    for (Iterator iterator = relpathToFile.keySet().iterator(); iterator.hasNext();) {
      String path = (String)iterator.next();
      File file = relpathToFile.get(path);
      addFileToZip(zos, file, path, null, null);
    }

    zis.close();
    zos.close();
  }

}