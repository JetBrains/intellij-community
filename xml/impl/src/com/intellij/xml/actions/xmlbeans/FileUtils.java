/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.actions.xmlbeans;


import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * @author Konstantin Bulenkov
 */
public class FileUtils {
  @NonNls
  public static final String CLASS_RESOURCE_STRING = "*?.class";
  @NonNls
  private static final String SOAP_ADDRESS = "soap:address";

  private FileUtils() {
  }

  public static File saveStreamContentAsFile(String fullFileName, InputStream stream) throws IOException {
    fullFileName = findFreeFileName(fullFileName);
    OutputStream ostream = new FileOutputStream(fullFileName);
    byte[] buf = new byte[8192];

    while(true) {
      int read = stream.read(buf,0,buf.length);
      if (read == -1) break;
      ostream.write(buf,0,read);
    }
    ostream.flush();
    ostream.close();
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


  public static void saveText(VirtualFile virtualFile, String text) throws IOException {
    VfsUtil.saveText(virtualFile, text);
  }

  public static boolean copyFile(File in, File out) {
    try {
      FileInputStream fis = new FileInputStream(in);
      FileOutputStream fos = new FileOutputStream(out);
      byte[] buf = new byte[1024];
      int i;
      while ((i = fis.read(buf)) != -1) {
        fos.write(buf, 0, i);
      }
      fis.close();
      fos.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
