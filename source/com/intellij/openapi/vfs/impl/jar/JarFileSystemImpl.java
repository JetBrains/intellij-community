package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.ZipFile;

public class JarFileSystemImpl extends JarFileSystem implements ApplicationComponent {
  private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private final Lock r = rwl.readLock();
  private final Lock w = rwl.writeLock();

  private Set<String> myNoCopyJarPaths = new ConcurrentHashSet<String>();
  @NonNls private static final String IDEA_JARS_NOCOPY = "idea.jars.nocopy";
  @NonNls private static final String IDEA_JAR = "idea.jar";

  private final Map<String, JarHandler> myHandlers = new HashMap<String, JarHandler>();

  public JarFileSystemImpl(MessageBus bus) {
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) { }

      public void after(final List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event.getFileSystem() instanceof LocalFileSystem) {
            final String path = event.getPath();
            List<String> jarPaths = new ArrayList<String>();
            r.lock();
            try {
              jarPaths.addAll(myHandlers.keySet());
            }
            finally{
              r.unlock();
            }

            for (String jarPath : jarPaths) {
              if (FileUtil.startsWith(jarPath.substring(0, jarPath.length() - JAR_SEPARATOR.length()), path, SystemInfo.isFileSystemCaseSensitive)) {
                markDirty(jarPath);
              }
            }
          }
        }
      }
    });
  }

  private void markDirty(final String path) {
    r.lock();
    final JarHandler handler;
    try {
      handler = myHandlers.remove(path);
    }
    finally {
      r.unlock();
    }

    if (handler != null) {
      handler.markDirty();
    }
  }

  @NotNull
  public String getComponentName() {
    return "JarFileSystem";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void setNoCopyJarForPath(String pathInJar) {
    int index = pathInJar.indexOf(JAR_SEPARATOR);
    if (index < 0) return;
    String path = pathInJar.substring(0, index);
    path = path.replace('/', File.separatorChar);
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path = path.toLowerCase();
  }
    myNoCopyJarPaths.add(path);
  }


  public VirtualFile getVirtualFileForJar(VirtualFile entryVFile) {
    final String path = entryVFile.getPath();
    String localPath = path.substring(0, path.indexOf(JarFileSystem.JAR_SEPARATOR));
    return LocalFileSystem.getInstance().findFileByPath(localPath);
  }

  public ZipFile getJarFile(VirtualFile entryVFile) throws IOException {
    JarHandler handler = getHandler(entryVFile);

    return handler.getZip();
  }

  private JarHandler getHandler(final VirtualFile entryVFile) {
    final String jarRootPath = extractRootPath(entryVFile.getPath());

    JarHandler handler;
    r.lock();
    try {
      handler = myHandlers.get(jarRootPath);
      if (handler == null) {
        r.unlock();
        w.lock();
        try {
          handler = myHandlers.get(jarRootPath);
          if (handler == null) {
            handler = new JarHandler(this, jarRootPath.substring(0, jarRootPath.length() - JAR_SEPARATOR.length()));
            myHandlers.put(jarRootPath, handler);
          }
        }
        finally {
          r.lock();
          w.unlock();
        }
      }
    }
    finally{
      r.unlock();
    }

    return handler;
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public String extractPresentableUrl(String path) {
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    path = super.extractPresentableUrl(path);
    return path;
  }

  public String extractRootPath(@NotNull final String path) {
    return path.substring(0, path.indexOf(JAR_SEPARATOR) + JAR_SEPARATOR.length());
  }

  public boolean isCaseSensitive() {
    return true;
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return getHandler(fileOrDirectory).exists(fileOrDirectory);
  }

  public InputStream getInputStream(final VirtualFile file) throws IOException {
    return getHandler(file).getInputStream(file);
  }

  public long getLength(final VirtualFile file) {
    return getHandler(file).getLength(file);
  }

  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws
                                                                                                                                 IOException {
    return getHandler(file).getOutputStream(file, requestor, modStamp, timeStamp);
  }

  public boolean isMakeCopyOfJar(File originalJar) {
    String property = System.getProperty(IDEA_JARS_NOCOPY);
    if (Boolean.TRUE.toString().equalsIgnoreCase(property)) return false;

    String path = originalJar.getPath();
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path = path.toLowerCase();
    }
    if (myNoCopyJarPaths.contains(path)) return false;

    String name = originalJar.getName();
    if (name.equalsIgnoreCase(IDEA_JAR)) {
      if (originalJar.getParent().equalsIgnoreCase(PathManager.getLibPath())) {
        return false;
      }
    }

    return true;
  }

  public long getTimeStamp(final VirtualFile file) {
    return getHandler(file).getTimeStamp(file);
  }

  public boolean isDirectory(final VirtualFile file) {
    return getHandler(file).isDirectory(file);
  }

  public boolean isWritable(final VirtualFile file) {
    return getHandler(file).isDirectory(file);
  }

  public String[] list(final VirtualFile file) {
    return getHandler(file).list(file);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    getHandler(file).setTimeStamp(file, modstamp);
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    getHandler(file).setWritable(file, writableFlag);
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public VirtualFile copyFile(Object requestor, VirtualFile vFile, VirtualFile newParent, final String copyName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public int getRank() {
    return 2;
  }
}
