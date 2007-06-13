package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;

class FileLoader extends Loader {
  private File myRootDir;
  private String myRootDirAbsolutePath;

  @SuppressWarnings({"HardCodedStringLiteral"})
  FileLoader(URL url) throws IOException {
    super(url);
    if (!"file".equals(url.getProtocol())) {
      throw new IllegalArgumentException("url");
    }
    else {
      final String s = FileUtil.unquote(url.getFile());
      myRootDir = new File(s);
      myRootDirAbsolutePath = myRootDir.getAbsolutePath();
    }
  }

  // True -> class file
  private void buildPackageCache(final File dir, ClasspathCache cache) {
    cache.addResourceEntry(getRelativeResourcePath(dir), this);

    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }

    boolean containsClasses = false;
    for (File file : files) {
      final boolean isClass = file.getPath().endsWith(UrlClassLoader.CLASS_EXTENSION);
      if (isClass) {
        if (!containsClasses) {
          cache.addResourceEntry(getRelativeResourcePath(file), this);
          containsClasses = true;
        }
      }
      else {
        buildPackageCache(file, cache);
      }
    }
  }

  private String getRelativeResourcePath(final File file) {
    String relativePath = file.getAbsolutePath().substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
    return relativePath;
  }

  @Nullable
  Resource getResource(final String name, boolean flag) {
    try {
      final URL url = new URL(getBaseURL(), name);
      if (!url.getFile().startsWith(getBaseURL().getFile())) return null;

      final File file = new File(myRootDir, name.replace('/', File.separatorChar));
      if (file.exists()) return new MyResource(name, url, file);
    }
    catch (Exception exception) {
      return null;
    }
    return null;
  }

  void buildCache(final ClasspathCache cache) throws IOException {
    File index = new File(myRootDir, "classpath.index");
    if (index.exists()) {
      BufferedReader reader = new BufferedReader(new FileReader(index));
      try {
        do {
          String line = reader.readLine();
          if (line == null) break;
          cache.addResourceEntry(line, this);
        }
        while (true);
      }
      finally {
        reader.close();
      }
    }
    else {
      cache.addResourceEntry("foo.class", this);
      cache.addResourceEntry("bar.properties", this);
      buildPackageCache(myRootDir, cache);
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
      return -1;
    }

    public String toString() {
      return myFile.getAbsolutePath();
    }
  }

  @NonNls
  public String toString() {
    return "FileLoader [" + myRootDir + "]";
  }
}
