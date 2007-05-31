package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.TimedComputable;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private URL myURL;
  private final boolean myCanLockJar;
  private final boolean myUseCache;
  private static boolean myDebugTime = false;

  //private SoftReference<ZipFile> myZipFileRef;
  private final TimedComputable<ZipFile> myZipFileRef = new TimedComputable<ZipFile>(null) {
    @NotNull
    protected ZipFile calc() {
      try {
        final ZipFile zipFile = _getZipFile();
        if (zipFile == null) throw new RuntimeException("Can't load zip file");
        return zipFile;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  private TObjectIntHashMap<String> myPackages = null;
  @NonNls private static final String JAR_PROTOCOL = "jar";
  @NonNls private static final String FILE_PROTOCOL = "file";
  private static final long NS_THRESHOLD = 10000000;

  JarLoader(URL url, boolean canLockJar, boolean useCache) throws IOException {
    super(new URL(JAR_PROTOCOL, "", -1, url + "!/"));
    myURL = url;
    myCanLockJar = canLockJar;
    myUseCache = useCache;
  }

  @Nullable
  private ZipFile getZipFile() throws IOException {
    if (myCanLockJar) {
      return myZipFileRef.acquire();
    }
    else {
      return _getZipFile();
    }
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
    if (myPackages != null || !myUseCache) return;
    myPackages = new TObjectIntHashMap<String>();
    myPackages.put("", 0);

    final ZipFile zipFile = getZipFile();
    if (zipFile == null) return;
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();

    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      final String name = zipEntry.getName();

      final int i = name.lastIndexOf("/");
      String packageName = i > 0 ? name.substring(0, i) : "";

      if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
        myPackages.put(packageName, 1);
      } else {
        if (!myPackages.containsKey(packageName)) {
          myPackages.put(packageName, 0);
        }
      }
    }

    releaseZipFile(zipFile);
  }

  private void releaseZipFile(final ZipFile zipFile) throws IOException {
    if (myCanLockJar) {
      myZipFileRef.release();
    }
    else {
      zipFile.close();
    }
  }

  @Nullable
  Resource getResource(String name, boolean flag) {
    final long started = myDebugTime ? System.nanoTime():0;
    try {
      initPackageCache();

      if (myUseCache) {
        String packageName = getPackageName(name);

        if (!myPackages.containsKey(packageName)) return null;

        if (name.endsWith(UrlClassLoader.CLASS_EXTENSION) && myPackages.get(packageName) == 0) {
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
    } finally {
      final long doneFor = myDebugTime ? (System.nanoTime() - started):0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor/1000000) + " ms for jar loader get resource:"+name);
      }
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

      final boolean[] wasReleased = new boolean[]{false};

      try {

        final InputStream inputStream = file.getInputStream(myEntry);
        return new FilterInputStream(inputStream) {
          private boolean myClosed = false;

          public void close() throws IOException {
            super.close();
            if (!myClosed) {
              releaseZipFile(file);
            }
            myClosed = true;
            wasReleased[0] = true;
          }
        };
      }
      catch (IOException e) {
        e.printStackTrace();
        assert !wasReleased[0];
        releaseZipFile(file);
        return null;
      }
    }

    public int getContentLength() {
      return (int)myEntry.getSize();
    }
  }
}
