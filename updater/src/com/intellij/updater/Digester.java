package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Digester {
  public static Map<String, Long> digestFiles(File dir, List<String> ignoredFiles, UpdaterUI ui)
    throws IOException, OperationCancelledException {
    Map<String, Long> result = new HashMap<String, Long>();

    LinkedHashSet<String> paths = Utils.collectRelativePaths(dir);
    for (String each : paths) {
      if (ignoredFiles.contains(each)) continue;
      ui.setStatus(each);
      ui.checkCancelled();
      result.put(each, digestFile(new File(dir, each)));
    }
    return result;
  }

  public static long digestFile(File file) throws IOException {
    if (Utils.isZipFile(file.getName())) {
      ZipFile zipFile;
      try {
        zipFile = new ZipFile(file);
      }
      catch (IOException e) {
        return doDigestRegularFile(file);
      }

      try {
        return doDigestZipFile(zipFile);
      }
      finally {
        zipFile.close();
      }
    }
    return doDigestRegularFile(file);
  }

  private static long doDigestRegularFile(File file) throws IOException {
    InputStream in = new BufferedInputStream(new FileInputStream(file));
    try {
      return digestStream(in);
    }
    finally {
      in.close();
    }
  }

  private static long doDigestZipFile(ZipFile zipFile) throws IOException {
    List<ZipEntry> sorted = new ArrayList<ZipEntry>();

    Enumeration<? extends ZipEntry> temp = zipFile.entries();
    while (temp.hasMoreElements()) {
      ZipEntry each = temp.nextElement();
      if (!each.isDirectory()) sorted.add(each);
    }

    Collections.sort(sorted, new Comparator<ZipEntry>() {
      public int compare(ZipEntry o1, ZipEntry o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    CRC32 crc = new CRC32();
    for (ZipEntry each : sorted) {
      InputStream in = zipFile.getInputStream(each);
      try {
        doDigestStream(in, crc);
      }
      finally {
        in.close();
      }
    }
    return crc.getValue();
  }

  public static long digestStream(InputStream in) throws IOException {
    CRC32 crc = new CRC32();
    doDigestStream(in, crc);
    return crc.getValue();
  }

  private static void doDigestStream(InputStream in, CRC32 crc) throws IOException {
    final byte[] BUFFER = new byte[65536];
    int size;
    while ((size = in.read(BUFFER)) != -1) {
      crc.update(BUFFER, 0, size);
    }
  }
}
