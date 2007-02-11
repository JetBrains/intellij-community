package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private URL myURL;
  private final boolean myCanLockJar;
  private final boolean myUseCache;
  private SoftReference<ZipFile> myZipFileRef;
  private Map<String,Boolean> myDirectories = null;
  @NonNls private static final String JAR_PROTOCOL = "jar";
  @NonNls private static final String FILE_PROTOCOL = "file";

  JarLoader(URL url, boolean canLockJar, boolean useCache) throws IOException {
    super(new URL(JAR_PROTOCOL, "", -1, url + "!/"));
    myURL = url;
    myCanLockJar = canLockJar;
    myUseCache = useCache;
  }

  @Nullable
  private ZipFile getZipFile() throws IOException {
    ZipFile zipFile = myZipFileRef != null ? myZipFileRef.get() : null;
    if (zipFile != null) return zipFile;
    if (myCanLockJar) {
      zipFile = _getZipFile();
      myZipFileRef = new SoftReference<ZipFile>(zipFile);
      return zipFile;
    }

    return _getZipFile();
  }

  @Nullable
  private ZipFile _getZipFile() throws IOException {
    if (FILE_PROTOCOL.equals(myURL.getProtocol())) {
      String s = FileUtil.unquote(myURL.getFile());
      if (!(new File(s)).exists()) {
        throw new FileNotFoundException(s);
      }
      else {
        return new ZipFile(s);
      }
    }

    return null;
  }

  private void initPackageCache() throws IOException {
    if (myDirectories != null || !myUseCache) return;
    myDirectories = new HashMap<String,Boolean>();
    myDirectories.put("",Boolean.FALSE);

    final ZipFile zipFile = getZipFile();
    if (zipFile == null) return;
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();

    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      final String name = zipEntry.getName();

      final int i = name.lastIndexOf("/");
      String packageName = i > 0 ? name.substring(0, i) : "";

      if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
        myDirectories.put(packageName,Boolean.TRUE);
      } else {
        final Boolean status = myDirectories.get(packageName);
        myDirectories.put(packageName,status != Boolean.TRUE ? Boolean.FALSE:Boolean.TRUE);
      }
    }

    releaseZipFile(zipFile);
  }

  private void releaseZipFile(final ZipFile zipFile) throws IOException {
    if (!myCanLockJar) {
      zipFile.close();
    }
  }

  @Nullable
  Resource getResource(String name, boolean flag) {
    try {
      initPackageCache();

      if (myUseCache) {
        String packageName = getPackageName(name);

        final Boolean hasClassFiles = myDirectories.get(packageName);
        if (hasClassFiles == null) return null;

        if ( name.endsWith(UrlClassLoader.CLASS_EXTENSION) &&
              !hasClassFiles.booleanValue()
            ) {
          return null;
        }
      }

      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        ZipEntry entry = file.getEntry(name);
        if (entry != null) return new MyResource(entry, new URL(getBaseURL(), name));
      }
      finally {
        releaseZipFile(file);
      }
    }
    catch (Exception e) {
      return null;
    }

    return null;
  }

  private static String getPackageName(final String name) {
    final int i = name.lastIndexOf("/");
    if (i < 0) return "";
    return name.substring(0, i);
  }

  private class MyResource extends Resource {
    private final ZipEntry myEntry;
    private final URL myUrl;

    public MyResource(ZipEntry name, URL url) {
      myEntry = name;
      myUrl = url;
    }

    public String getName() {
      return myEntry.getName();
    }

    public URL getURL() {
      return myUrl;
    }

    public URL getCodeSourceURL() {
      return myURL;
    }

    @Nullable
    public InputStream getInputStream() throws IOException {
      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        final InputStream inputStream = file.getInputStream(myEntry);
        return new FilterInputStream(inputStream) {
          public void close() throws IOException {
            super.close();
            releaseZipFile(file);
          }
        };
      }
      catch (IOException e) {
        releaseZipFile(file);
        return null;
      }
    }

    public int getContentLength() {
      return (int)myEntry.getSize();
    }
  }
}
