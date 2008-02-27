/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class URLUtil {
  private URLUtil() {
  }

  /**
   * Opens a url stream. The semantics is the sames as {@link java.net.URL#openStream()}. The
   * separate method is needed, since jar URLs open jars via JarFactory and thus keep them
   * mapped into memory.
   */
  @NotNull
  public static InputStream openStream(final URL url) throws IOException {
    @NonNls final String protocol = url.getProtocol();
    try {
      if (protocol.equals("jar")) {
        return openJarStream(url);
      }

      return url.openStream();
    }
    catch(FileNotFoundException ex) {
      String file = null;
      if (protocol.equals("file")) {
        file = url.getFile();
      }
      else if (protocol.equals("jar")) {
        int pos = url.getFile().indexOf("!");
        if (pos >= 0) {
          file = url.getFile().substring(pos+1);
        }
      }
      if (file != null && file.startsWith("/")) {
        InputStream resourceStream = URLUtil.class.getResourceAsStream(file);
        if (resourceStream != null) return resourceStream;
      }
      throw ex;
    }
  }

  @NotNull
  private static InputStream openJarStream(final URL url) throws IOException {
    String file = url.getFile();
    assert file.startsWith("file:");
    file = file.substring("file:".length());
    assert file.indexOf("!/") > 0;

    String resource = file.substring(file.indexOf("!/") + 2);
    file = file.substring(0, file.indexOf("!"));
    final ZipFile zipFile = new ZipFile(FileUtil.unquote(file));
    final ZipEntry zipEntry = zipFile.getEntry(resource);
    if (zipEntry == null) throw new FileNotFoundException("Entry " + resource + " not found in " + file);
    return new FilterInputStream(zipFile.getInputStream(zipEntry)) {
        public void close() throws IOException {
          super.close();
          zipFile.close();
        }
      };
  }
}
