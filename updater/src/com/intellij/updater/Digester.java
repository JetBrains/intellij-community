package com.intellij.updater;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

public class Digester {
  public static long INVALID = -1;
  public static long DIRECTORY = -2;

  private final String myAlgorithm;

  public Digester(String algorithm) {
    myAlgorithm = algorithm;
  }

  public long digestRegularFile(File file) throws IOException {
    if (file.isDirectory()) {
      return DIRECTORY;
    }
    InputStream in = new BufferedInputStream(new FileInputStream(file));
    try {
      return digestStream(in);
    }
    finally {
      in.close();
    }
  }

  public long digestZipFile(File file) throws IOException {
    ZipFile zipFile;
    try {
      zipFile = new ZipFile(file);
    } catch (ZipException e) {
      // This was not a zip file...
      return digestRegularFile(file);
    }
    try {
      List<ZipEntry> sorted = new ArrayList<ZipEntry>();

      Enumeration<? extends ZipEntry> temp = zipFile.entries();
      while (temp.hasMoreElements()) {
        ZipEntry each = temp.nextElement();
        if (!each.isDirectory()) sorted.add(each);
      }

      Collections.sort(sorted, new Comparator<ZipEntry>() {
        @Override
        public int compare(ZipEntry o1, ZipEntry o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      Checksum checksum = createChecksum(myAlgorithm);

      for (ZipEntry each : sorted) {
        InputStream in = zipFile.getInputStream(each);
        try {
          doDigestStream(in, checksum);
        }
        finally {
          in.close();
        }
      }
      return checksum.getValue();
    } finally {
      zipFile.close();
    }
  }

  private static Checksum createChecksum(String algorithm) {
    if (algorithm != null && !algorithm.equals("crc")) {
      return new MessageDigestChecksum(algorithm);
    }
    return new CRC32();
  }

  public long digestStream(InputStream in) throws IOException {
    Checksum checksum = createChecksum(myAlgorithm);
    doDigestStream(in, checksum);
    return checksum.getValue();
  }

  private static void doDigestStream(InputStream in, Checksum checksum) throws IOException {
    final byte[] BUFFER = new byte[65536];
    int size;
    while ((size = in.read(BUFFER)) != -1) {
      checksum.update(BUFFER, 0, size);
    }
  }

  public static boolean isValidAlgorithm(String hashAlgorithm) {
    try {
      return createChecksum(hashAlgorithm) != null;
    }
    catch (Exception e) {
      return false;
    }
  }

  private static class MessageDigestChecksum implements Checksum {
    private MessageDigest digest;

    public MessageDigestChecksum(String algorithm) {
      try {
        digest = MessageDigest.getInstance(algorithm);
      }
      catch (NoSuchAlgorithmException e) {
        throw new IllegalArgumentException("Algorithm must be verified using isValidAlgorithm() before creating a MessageDigestChecksum!");
      }
    }

    @Override
    public void update(int b) {
      throw new NotImplementedException();
    }

    @Override
    public void update(byte[] b, int off, int len) {
      digest.update(b, off, len);
    }

    @Override
    public long getValue() {
      long result = 0;
      long mult = 1;
      for (byte b : digest.digest()) {
        result += b * mult;
        mult *= 256;
      }
      return result;
    }

    @Override
    public void reset() {
      digest.reset();
    }
  }
}
