/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.io;

import java.io.*;

public class StreamUtil {
  private StreamUtil() {
  }

  /**
   * @param inputStream source stream
   * @param outputStream destination stream
   * @return bytes copied
   * @throws IOException
   */
  public static int copyStreamContent(InputStream inputStream, OutputStream outputStream) throws IOException {
    final byte[] buffer = new byte[10 * 1024];
    int count;
    int total = 0;
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      total += count;
    }
    return total;
  }

  public static byte[] loadFromStream(InputStream inputStream) throws IOException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      copyStreamContent(inputStream, outputStream);
    }
    finally {
      inputStream.close();
    }
    return outputStream.toByteArray();
  }

  public static String readText(InputStream inputStream) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    return new String(data);
  }

  public static String readText(InputStream inputStream, String encoding) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    return new String(data, encoding);
  }

  public static String convertSeparators(String s) {
    try {
      return new String(readTextAndConvertSeparators(new StringReader(s)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static char[] readTextAndConvertSeparators(Reader reader) throws IOException {
    char[] buffer = readText(reader);

    int dst = 0;
    char prev = ' ';
    for (char c : buffer) {
      switch (c) {
        case'\r':
          buffer[dst++] = '\n';
          break;
        case'\n':
          if (prev != '\r') {
            buffer[dst++] = '\n';
          }
          break;
        default:
          buffer[dst++] = c;
          break;
      }
      prev = c;
    }

    char[] chars = new char[dst];
    System.arraycopy(buffer, 0, chars, 0, chars.length);
    return chars;
  }

  private static char[] readText(Reader reader) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();

    {
      char[] buffer = new char[2048];
      while (true) {
        int read = reader.read(buffer);
        if (read < 0) break;
        writer.write(buffer, 0, read);
      }
    }

    return writer.toCharArray();
  }
}
