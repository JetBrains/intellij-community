package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.jar.JarFileSystemEx;
import com.intellij.util.containers.HashMap;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.zip.ZipFile;

public class JarFileSystemImpl extends JarFileSystemEx implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl");

  final Object LOCK = new Object();

  private HashMap<String, JarFileInfo> myPathToFileInfoMap = new HashMap<String, JarFileInfo>();
  private HashSet<String> myNoCopyJarPaths = new HashSet<String>();
  private HashSet<String> myContentChangingJars = new HashSet<String>();

  private VirtualFileManagerEx myManager;

  public JarFileSystemImpl(LocalFileSystem localFileSystem) {

    localFileSystem.addVirtualFileListener(
      new VirtualFileAdapter() {
        public void contentsChanged(VirtualFileEvent event) {
          JarFileInfo info = myPathToFileInfoMap.get(event.getFile().getPath());
          if (info != null) {
            refreshInfo(info, true);
          }
        }

        public void fileDeleted(VirtualFileEvent event) {
          VirtualFile parent = event.getParent();
          if (parent != null) {
            String oldPath = parent.getPath() + '/' + event.getFileName();
            JarFileInfo info = myPathToFileInfoMap.get(oldPath);
            if (info != null) {
              refreshInfo(info, true);
            }
          }
        }

        public void fileMoved(VirtualFileMoveEvent event) {
          //TODO: real move
          String oldPath = event.getOldParent().getPath() + '/' + event.getFileName();
          JarFileInfo info = myPathToFileInfoMap.get(oldPath);
          if (info != null) {
            refreshInfo(info, true);
          }
        }

        public void propertyChanged(VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            //TODO: real rename
            VirtualFile parent = event.getParent();
            String oldPath = (parent == null ? "" : parent.getPath() + '/') + event.getOldValue();
            JarFileInfo info = myPathToFileInfoMap.get(oldPath);
            if (info != null) {
              refreshInfo(info, true);
            }
          }
        }
      }
    );
  }

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

  boolean isValid(JarFileInfo info) {
    synchronized (LOCK) {
      String path = info.getFile().getPath();
      JarFileInfo info1 = myPathToFileInfoMap.get(path);
      return info1 == info;
    }
  }

  public VirtualFile getVirtualFileForJar(VirtualFile entryVFile) {
    String rootPath = ((VirtualFileImpl)entryVFile).getFileInfo().getRootPath();
    LOG.assertTrue(rootPath.endsWith(JAR_SEPARATOR));
    return LocalFileSystem.getInstance().findFileByPath(rootPath.substring(0, rootPath.length() - JAR_SEPARATOR.length()));
  }

  public ZipFile getJarFile(VirtualFile entryVFile) throws IOException {
    synchronized (LOCK) {
      return ((VirtualFileImpl)entryVFile).getFileInfo().getZipFile();
    }
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(String path) {
    int index = path.lastIndexOf(JAR_SEPARATOR);
    if (index < 0) return null;
    String jarPath = path.substring(0, index);
    String relPath = path.substring(index + JAR_SEPARATOR.length());

    synchronized (LOCK) {
      JarFileInfo info = myPathToFileInfoMap.get(jarPath);
      if (info == null) {
        if (myContentChangingJars.contains(jarPath)) return null;
        File file = new File(jarPath);
        try {
          jarPath = file.getCanonicalPath();
        }
        catch (IOException e) {
          LOG.info("Could not fetch canonical path for " + jarPath);
          return null;
        }

        if (jarPath == null) {
          LOG.info("Canonical path is null for " + file.getPath());
          return null;
        }

        file = new File(jarPath);
        info = myPathToFileInfoMap.get(jarPath);
        if (info != null) {
          return info.findFile(relPath);
        }

        if (myContentChangingJars.contains(jarPath)) return null;

        if (!file.exists()) {
          LOG.info("Jar file " + file.getPath() + " not found");
          return null;
        }

        File mirrorFile = getMirrorFile(file);
        info = new JarFileInfo(this, file, mirrorFile);

        myPathToFileInfoMap.put(jarPath, info);
      }

      return info.findFile(relPath);
    }
  }

  public String extractPresentableUrl(String path) {
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    path = super.extractPresentableUrl(path);
    return path;
  }

  public void refresh(boolean asynchronous) {
    JarFileInfo[] infos;
    synchronized (LOCK) {
      infos = myPathToFileInfoMap.values().toArray(new JarFileInfo[myPathToFileInfoMap.size()]);
    }

    for (int i = 0; i < infos.length; i++) {
      refreshInfo(infos[i], asynchronous);
    }
  }

  private void refreshInfo(final JarFileInfo info, boolean asynchronous) {
    ModalityState modalityState = EventQueue.isDispatchThread() ? ModalityState.current() : ModalityState.NON_MMODAL;

    getManager().beforeRefreshStart(asynchronous, modalityState, null);

    if (!info.getFile().exists()) {
      LOG.info("file deleted");

      final VirtualFile rootFile = info.getRootFile();
      getManager().addEventToFireByRefresh(
        new Runnable() {
          public void run() {
            fireBeforeFileDeletion(null, rootFile);
            synchronized (LOCK) {
              myPathToFileInfoMap.remove(info.getFile().getPath());
            }
            info.close();
            fireFileDeleted(null, rootFile, rootFile.getName(), true, null);
          }
        },
        asynchronous,
        modalityState
      );
    }
    else if (info.getTimeStamp() != info.getFile().lastModified()) {
      LOG.info("timestamp changed");

      final VirtualFile rootFile = info.getRootFile();
      getManager().addEventToFireByRefresh(
        new Runnable() {
          public void run() {
            if (!rootFile.isValid()) return;
            fireBeforeFileDeletion(null, rootFile);
            final String path = info.getFile().getPath();
            synchronized (LOCK) {
              myPathToFileInfoMap.remove(path);
              myContentChangingJars.add(path);
            }
            info.close();
            fireFileDeleted(null, rootFile, rootFile.getName(), true, null);
            synchronized (LOCK) {
              myContentChangingJars.remove(path);
            }
            fireFileCreated(null, findFileByPath(path + JAR_SEPARATOR));
          }
        },
        asynchronous,
        modalityState
      );
    }

    getManager().afterRefreshFinish(asynchronous, modalityState);
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public File getMirrorFile(File originalFile) {
    if (!isMakeCopyOfJar(originalFile)) return originalFile;

    String folderPath = PathManager.getSystemPath() + File.separatorChar + "jars";
    if (!new File(folderPath).exists()) {
      if (!new File(folderPath).mkdirs()) {
        return originalFile;
      }
    }

    String fileName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    return new File(folderPath, fileName);
  }

  private boolean isMakeCopyOfJar(File originalJar) {
    String property = System.getProperty("idea.jars.nocopy");
    if ("true".equalsIgnoreCase(property)) return false;

    String path = originalJar.getPath();
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path = path.toLowerCase();
    }
    if (myNoCopyJarPaths.contains(path)) return false;

    String name = originalJar.getName();
    if (name.equalsIgnoreCase("idea.jar")) {
      if (originalJar.getParent().equalsIgnoreCase(PathManager.getLibPath())) {
        return false;
      }
    }

    return true;
  }

  public VirtualFileManagerEx getManager() {
    if (myManager == null) {
      myManager = (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
    }
    return myManager;
  }
}