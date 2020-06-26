// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.xmlbeans;


import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * @author Konstantin Bulenkov
 */
public final class FileUtils {
  @NonNls
  public static final String CLASS_RESOURCE_STRING = "*?.class";
  @NonNls
  private static final String SOAP_ADDRESS = "soap:address";

  private FileUtils() {
  }

  public static File saveStreamContentAsFile(String fullFileName, InputStream stream) throws IOException {
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
    return new File(fullFileName);
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

  public static boolean copyFile(File in, File out) {
    try (FileInputStream fis = new FileInputStream(in);
         FileOutputStream fos = new FileOutputStream(out)) {
      byte[] buf = new byte[1024];
      int i;
      while ((i = fis.read(buf)) != -1) {
        fos.write(buf, 0, i);
      }
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }
}
