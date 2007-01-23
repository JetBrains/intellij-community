package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarFileSystemImpl extends JarFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl");

  final Object LOCK = new Object();

  private HashMap<String, JarFileInfo> myPathToFileInfoMap = new HashMap<String, JarFileInfo>();
  private HashSet<String> myNoCopyJarPaths = new HashSet<String>();

  private VirtualFileManagerEx myManager;
  @NonNls private static final String JARS_FOLDER = "jars";
  @NonNls private static final String IDEA_JARS_NOCOPY = "idea.jars.nocopy";
  @NonNls private static final String IDEA_JAR = "idea.jar";

  public JarFileSystemImpl(LocalFileSystem localFileSystem) {

    localFileSystem.addVirtualFileListener(
      new VirtualFileAdapter() {
        public void contentsChanged(VirtualFileEvent event) {
          JarFileInfo info = myPathToFileInfoMap.get(event.getFile().getPath());
          if (info != null) {
            refreshInfo(info, true, false);
          }
        }

        public void fileDeleted(VirtualFileEvent event) {
          VirtualFile parent = event.getParent();
          if (parent != null) {
            String oldPath = parent.getPath() + '/' + event.getFileName();
            JarFileInfo info = myPathToFileInfoMap.get(oldPath);
            if (info != null) {
              refreshInfo(info, true, false);
            }
          }
        }

        public void fileMoved(VirtualFileMoveEvent event) {
          //TODO: real move
          String oldPath = event.getOldParent().getPath() + '/' + event.getFileName();
          JarFileInfo info = myPathToFileInfoMap.get(oldPath);
          if (info != null) {
            refreshInfo(info, true, false);
          }
        }

        public void propertyChanged(VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            //TODO: real rename
            VirtualFile parent = event.getParent();
            String oldPath = (parent == null ? "" : parent.getPath() + '/') + event.getOldValue();
            JarFileInfo info = myPathToFileInfoMap.get(oldPath);
            if (info != null) {
              refreshInfo(info, true, false);
            }
          }
        }
      }
    );
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

  public void refresh(final boolean asynchronous) {
    JarFileInfo[] infos;
    synchronized (LOCK) {
      infos = myPathToFileInfoMap.values().toArray(new JarFileInfo[myPathToFileInfoMap.size()]);
    }

    final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(asynchronous);
    final VirtualFileManagerEx manager = getManager();
    manager.beforeRefreshStart(asynchronous, modalityState, null);

    final Ref<Integer> joinCount = new Ref<Integer>(infos.length);
    for (final JarFileInfo info : infos) {
      final VirtualFile localRoot = getVirtualFileForJar(info.getRootFile());
      if (localRoot != null) {
        localRoot.refresh(asynchronous, false, new Runnable() {
          public void run() {
            postAfterRefreshFinishWhenAllJoined(joinCount, manager, asynchronous, modalityState);
            if (!localRoot.isValid() || localRoot.getTimeStamp() != info.getTimeStamp()) {
              refreshInfo(info, asynchronous, false);
            }
          }
        });
      } else {
        refreshInfo(info, asynchronous, false);
        postAfterRefreshFinishWhenAllJoined(joinCount, manager, asynchronous, modalityState);
      }
    }
  }

  private static void postAfterRefreshFinishWhenAllJoined(final Ref<Integer> joinCount, 
                                                   final VirtualFileManagerEx manager,
                                                   final boolean asynchronous,
                                                   final ModalityState modalityState) {
    int cnt = joinCount.get();
    if (--cnt == 0) {
      manager.afterRefreshFinish(asynchronous, modalityState);
    }
    joinCount.set(cnt);
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull VirtualFile... files) {
    for (VirtualFile file : files) {
      String path = file.getPath();
      JarFileInfo jarFileInfo = myPathToFileInfoMap.get(path);
      if (jarFileInfo == null) {
        refreshAndFindFileByPath(path);
        continue;
      }
      refreshInfo(jarFileInfo, asynchronous, true);
    }
  }

  private void refreshInfo(final JarFileInfo info, boolean asynchronous, final boolean forceRefresh) {
    ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(asynchronous);

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
            fireFileDeleted(null, rootFile, rootFile.getName(), null);
          }
        },
        asynchronous,
        modalityState
      );
    }
    else if (info.getTimeStamp() != info.getFile().lastModified()) {
      LOG.info("timestamp changed");

      try {
        refreshFilesInJar(info, asynchronous, modalityState);
        info.setTimeStamp(info.getFile().lastModified());
      }
      catch (IOException e) {
        LOG.info("Could not read jar");
        forceRefreshInfo(info, asynchronous, modalityState);
      }
    }
    else if (forceRefresh) {
      forceRefreshInfo(info, asynchronous, modalityState);
    }

    getManager().afterRefreshFinish(asynchronous, modalityState);
  }

  private void forceRefreshInfo(final JarFileInfo info, final boolean asynchronous, final ModalityState modalityState) {
    LOG.info("refresh forced");

    final VirtualFile rootFile = info.getRootFile();
    getManager().addEventToFireByRefresh(
        new Runnable() {
        public void run() {
          if (!rootFile.isValid()) return;
          fireBeforeFileDeletion(null, rootFile);
          final String path = info.getFile().getPath();
          synchronized (LOCK) {
            myPathToFileInfoMap.remove(path);
          }
          info.close();
          fireFileDeleted(null, rootFile, rootFile.getName(), null);
          fireFileCreated(null, findFileByPath(path + JAR_SEPARATOR));
        }
      },
      asynchronous,
      modalityState
    );
  }

  private void refreshFilesInJar(final JarFileInfo info,
                                 final boolean asynchronous,
                                 final ModalityState modalityState) throws IOException {
    synchronized(LOCK) {
      final Enumeration<? extends ZipEntry> entries = info.getZipFile().entries();
      final Set<VirtualFileImpl> allFiles = info.getFiles();
      if (allFiles.isEmpty()) return;

      final Map<String, VirtualFileImpl> relPathToFileMap = new THashMap<String, VirtualFileImpl>();
      for (final VirtualFileImpl file : allFiles) {
        final String relPath = file.getRelativePath();
        relPathToFileMap.put(relPath, file);
      }

      final Set<VirtualFileImpl> deletedFiles = new HashSet<VirtualFileImpl>(allFiles);
      deletedFiles.remove(info.getRootFile());

      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        final String entryName = entry.getName();
        final VirtualFileImpl vFile = relPathToFileMap.get(entryName);
        if (vFile == null) {
          getManager().addEventToFireByRefresh(new Runnable() {
            public void run() {
              int i = entryName.lastIndexOf("/");
              String parentRelPath = i > 0 ? entryName.substring(i) : "";
              VirtualFileImpl parent = relPathToFileMap.get(parentRelPath);
              if (parent != null) {
                final VirtualFileImpl created = new VirtualFileImpl(info, parent, entryName, entry.isDirectory(), -1, -1);
                final VirtualFile[] children = parent.getChildren();
                VirtualFileImpl[] newChildren = new VirtualFileImpl[children.length + 1];
                System.arraycopy(children, 0, newChildren, 0, children.length);
                newChildren[children.length] = created;
                parent.myChildren = newChildren;
                myPathToFileInfoMap.put(entryName, info);
                fireFileCreated(null, created);
              }
            }
          }, asynchronous, modalityState);
        }
        else {
          VirtualFileImpl run = vFile;
          while(run != null) {
            deletedFiles.remove(run);
            run = (VirtualFileImpl)run.getParent();
          }

          if (!entry.isDirectory() || !vFile.isDirectory()) {
            if (vFile.getCRC() != entry.getCrc()/* || vFile.getTimeStamp() != entry.getTime()*/) {
              getManager().addEventToFireByRefresh(new Runnable() {
              public void run() {
                if (vFile.isValid()) {
                  final VirtualFileImpl parent = (VirtualFileImpl)vFile.getParent();
                  assert parent != null && parent.isValid();
                  final VirtualFile[] children = parent.getChildren();
                  int index = ArrayUtil.indexOf(children, vFile);

                  fireBeforeFileDeletion(null, vFile);
                  removeFile(vFile, info);
                  fireFileDeleted(null, vFile, vFile.getName(), parent);

                  final VirtualFile created = new VirtualFileImpl(info, parent, entryName, entry.isDirectory(), -1, -1);
                  myPathToFileInfoMap.put(entryName, info);
                  children[index] = created;
                  fireFileCreated(null, created);
                }
              }
            }, asynchronous, modalityState);
            }
          }
        }
      }

      for (final VirtualFileImpl file : deletedFiles) {
        getManager().addEventToFireByRefresh(new Runnable() {
            public void run() {
              if (file.isValid()) {
                final VirtualFileImpl parent = (VirtualFileImpl)file.getParent();
                assert parent != null && parent.isValid();
                final VirtualFile[] children = parent.getChildren();
                final int i = ArrayUtil.indexOf(children, file);
                LOG.assertTrue(i >= 0);
                parent.myChildren = ArrayUtil.remove(children, i);

                if (!deletedFiles.contains(parent)) {
                  fireBeforeFileDeletion(null, file);
                  removeFile(file, info);
                  fireFileDeleted(null, file, file.getName(), parent);
                }
              }
            }
          }, asynchronous, modalityState);
      }
    }
  }

  private void removeFile(final VirtualFileImpl file, final JarFileInfo info) {
    myPathToFileInfoMap.remove(file.getPath());
    info.removeFile(file);
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public File getMirrorFile(File originalFile) {
    if (!isMakeCopyOfJar(originalFile)) return originalFile;

    String folderPath = PathManager.getSystemPath() + File.separatorChar + JARS_FOLDER;
    if (!new File(folderPath).exists()) {
      if (!new File(folderPath).mkdirs()) {
        return originalFile;
      }
    }

    String fileName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    return new File(folderPath, fileName);
  }

  private boolean isMakeCopyOfJar(File originalJar) {
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

  public VirtualFileManagerEx getManager() {
    if (myManager == null) {
      myManager = (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
    }
    return myManager;
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vDir).getFile().getPath()));
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vDir).getFile().getPath()));
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vFile).getFile().getPath()));
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vFile).getFile().getPath()));
  }

  public VirtualFile copyFile(Object requestor, VirtualFile vFile, VirtualFile newParent, final String copyName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vFile).getFile().getPath()));
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", ((VirtualFileImpl)vFile).getFile().getPath()));
  }
}
