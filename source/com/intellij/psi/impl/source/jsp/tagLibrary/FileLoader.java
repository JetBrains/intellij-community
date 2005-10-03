package com.intellij.psi.impl.source.jsp.tagLibrary;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;

class FileLoader extends Loader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.jsp.tagLibrary.FileLoader");
  Resource getResource(final String name, boolean flag) {
    try {
      final URL url = new URL(getBaseURL(), name);
      if (!url.getFile().startsWith(getBaseURL().getFile())) return null;
      final File file = new File(dir, name.replace('/', File.separatorChar));
      if (file.exists()) return new MyResource(name, url, file);
    }
    catch (Exception exception) {
      return null;
    }
    return null;
  }

  private File dir;

  @SuppressWarnings({"HardCodedStringLiteral"})
    FileLoader(URL url) throws IOException {
    super(url);
    if (!"file".equals(url.getProtocol())) {
      throw new IllegalArgumentException("url");
    }
    else {
      final String s = url.toExternalForm();

      if (s.startsWith("file://")) {
        String path = s.substring("file://".length()).replace('/', File.separatorChar);
        if (SystemInfo.isUnix) path = "/" + path;
        dir = new File(path);
      }
      else if (s.startsWith("file:/")) {
        String path = s.substring("file:/".length()).replace('/', File.separatorChar);
        if (SystemInfo.isUnix) path = "/" + path;
        dir = new File(path);
      }
      else {
        LOG.assertTrue(false, s);
      }
    }
  }

  private class MyResource extends Resource {
    private final String myName;
    private final URL myUrl;
    private final File myFile;

    public MyResource(String name, URL url, File file) {
      myName = name;
      myUrl = url;
      myFile = file;
    }

    public String getName() {
      return myName;
    }

    public URL getURL() {
      return myUrl;
    }

    public URL getCodeSourceURL() {
      return getBaseURL();
    }

    public InputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(myFile));
    }

    public int getContentLength() throws IOException {
      return (int)myFile.length();
    }
  }
}
