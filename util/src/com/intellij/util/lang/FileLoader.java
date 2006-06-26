package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import gnu.trove.THashSet;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;

class FileLoader extends Loader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.lang.FileLoader");
  private THashSet<String> myPackages = null;
  private File myRootDir;
  private String myRootDirAbsolutePath;

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
        myRootDir = new File(path);
      }
      else if (s.startsWith("file:/")) {
        String path = s.substring("file:/".length()).replace('/', File.separatorChar);
        if (SystemInfo.isUnix) path = "/" + path;
        myRootDir = new File(path);
      }
      else {
        LOG.assertTrue(false, s);
      }

      myRootDirAbsolutePath = myRootDir.getAbsolutePath();
    }
  }

  private void buildPackageCache(final File dir) {
    if (!dir.isDirectory()) {
      return;
    }

    String relativePath = dir.getAbsolutePath().substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

    myPackages.add(relativePath);

    final File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      buildPackageCache(files[i]);
    }
  }

  Resource getResource(final String name, boolean flag) {
    if (myPackages == null) {
      initPackageCache();
    }

    try {
      String packageName = getPackageName(name);
      if (!myPackages.contains(packageName)) return null;

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

  private static String getPackageName(final String name) {
    final int i = name.lastIndexOf("/");
    if (i < 0) return "";
    return name.substring(0, i);
  }

  private void initPackageCache() {
    myPackages = new THashSet<String>();
    buildPackageCache(myRootDir);
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
