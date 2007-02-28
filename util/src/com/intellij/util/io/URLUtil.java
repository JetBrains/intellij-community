package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

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
    final String protocol = url.getProtocol();
    if (protocol.equals("jar")) {
      return openJarStream(url);
    }

    return url.openStream();
  }

  @NotNull
  private static InputStream openJarStream(final URL url) throws IOException {
    String file = url.getFile();
    assert file.startsWith("file:");
    file = file.substring("file:".length());
    assert file.indexOf("!/") > 0;

    String resource = file.substring(file.indexOf("!/") + 2);
    file = file.substring(0, file.indexOf("!"));
    final ZipFile zipFile = new ZipFile(file);
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
