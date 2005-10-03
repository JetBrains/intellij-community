package com.intellij.psi.impl.source.jsp.tagLibrary;

import org.jetbrains.annotations.NonNls;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private URL myURL;
  @NonNls private static final String JAR_PROTOCOL = "jar";
  @NonNls private static final String FILE_PROTOCOL = "file";

  JarLoader(URL url) throws IOException {
    super(new URL(JAR_PROTOCOL, "", -1, url + "!/"));
    myURL = url;
  }

  private ZipFile getZipFile() throws IOException {
    if (FILE_PROTOCOL.equals(myURL.getProtocol())) {
      String s = myURL.getFile().replace('/', File.separatorChar);
      if (!(new File(s)).exists()) throw new FileNotFoundException(s);
      else return new ZipFile(s);
    }

    return null;
  }

  Resource getResource(String name, boolean flag) {
    try {
      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        ZipEntry entry = file.getEntry(name);
        if (entry != null) return new MyResource(name, new URL(getBaseURL(), name));
      }
      finally {
        file.close();
      }
    }
    catch (Exception e) {
    }

    return null;
  }

  private class MyResource extends Resource {
    private final String myName;
    private final URL myUrl;

    public MyResource(String name, URL url) {
      myName = name;
      myUrl = url;
    }

    public String getName() {
      return myName;
    }

    public URL getURL() {
      return myUrl;
    }

    public URL getCodeSourceURL() {
      return myURL;
    }

    public InputStream getInputStream() throws IOException {
      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        final ZipEntry entry = file.getEntry(myName);
        if (entry == null) {
          file.close();
          return null;
        }
        final InputStream inputStream = new BufferedInputStream(file.getInputStream(entry));
        return new FilterInputStream(inputStream) {
          public void close() throws IOException {
            super.close();
            file.close();
          }
        };
      }
      catch (IOException e) {
        file.close();
        return null;
      }
    }

    public int getContentLength() {
      try {
        final ZipFile jarFile = getZipFile();
        if (jarFile == null) return -1;

        try {
          final ZipEntry entry = jarFile.getEntry(myName);
          if (entry == null) return -1;

          return (int)entry.getSize();
        }
        finally {
          jarFile.close();
        }
      }
      catch (IOException e) {
        return -1;
      }
    }
  }
}
