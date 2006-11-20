package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;
import java.util.HashMap;

class FileLoader extends Loader {
  private HashMap<String,Boolean> myDirectories = null; // True has class files inside, false otherwise
  private File myRootDir;
  private String myRootDirAbsolutePath;
  private final boolean myUseCache;

  @SuppressWarnings({"HardCodedStringLiteral"})
  FileLoader(URL url, boolean useCache) throws IOException {
    super(url);
    myUseCache = useCache;
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
  private boolean buildPackageCache(final File dir) {
    if (dir.getName().endsWith(UrlClassLoader.CLASS_EXTENSION)) return true; // optimization to prevent disc access for class files
    final File[] files = dir.listFiles();
    if (files == null) return true;

    boolean containFiles = false;
    for (File file : files) {
      containFiles |= buildPackageCache(file);
    }

    String relativePath = dir.getAbsolutePath().substring(myRootDirAbsolutePath.length());
    relativePath = relativePath.replace(File.separatorChar, '/');
    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

    myDirectories.put(relativePath,Boolean.valueOf(containFiles));
    return false;
  }

  @Nullable
  Resource getResource(final String name, boolean flag) {
    initPackageCache();

    try {
      if (myUseCache) {
        String packageName = getPackageName(name);
        final Boolean hasFiles = myDirectories.get(packageName);

        if (hasFiles == null) {
          return null;
        }

        if ( name.endsWith(UrlClassLoader.CLASS_EXTENSION) &&
              !hasFiles.booleanValue()
            ) {
          // package does not contain class files
          return null;
        }
      }

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
    if (myDirectories != null || !myUseCache) return;
    myDirectories = new HashMap<String,Boolean>();
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
      return -1;
    }

    public String toString() {
      return myFile.getAbsolutePath();
    }
  }
}
