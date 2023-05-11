// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.tools;

import java.io.*;

/**
 * @author Konstantin Bulenkov
 */
final class FileUtils {
  private FileUtils() {
  }

  public static void saveStreamContentAsFile(String fullFileName, InputStream stream) throws IOException {
    fullFileName = findFreeFileName(fullFileName);
    try (OutputStream ostream = new FileOutputStream(fullFileName)) {
      byte[] buf = new byte[8192];

      while (true) {
        int read = stream.read(buf, 0, buf.length);
        if (read == -1) break;
        ostream.write(buf, 0, read);
      }
      ostream.flush();
    }
    new File(fullFileName);
  }

  private static String findFreeFileName(String filename) {
    File f = new File(filename);
    if (! f.exists()) return filename;
    int dot = filename.lastIndexOf('.'); // we believe file has some ext. For instance,  ".wsdl"
    String name = filename.substring(0, dot);
    String ext = filename.substring(dot);
    int num = 0;
    do {
      f = new File(name + ++num + ext);
    } while (f.exists());
    return name + num + ext;
  }
}
