package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.vfs.local.win32.FileWatcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class LocalFileSystemImpl extends LocalFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");
  private VirtualFileManagerEx myManager = null;

  final Object LOCK = new Object();

  private final ArrayList<VirtualFile> myRoots = new ArrayList<VirtualFile>();

  private ArrayList<VirtualFile> myFilesToWatchManual = new ArrayList<VirtualFile>();
  private final HashSet<String> myDirtyFiles = new HashSet<String>(); // dirty files when FileWatcher is available
  private final HashSet<String> myDeletedFiles = new HashSet<String>();

  private Alarm mySynchronizeQueueAlarm;

  private File[] myCachedRoots;

  private final Map<VirtualFile,Object> myRefreshStatusMap = new WeakHashMap<VirtualFile, Object>(); // VirtualFile --> 'status'
  private static final Object DIRTY_STATUS = Key.create("DIRTY_STATUS");
  private static final Object DELETED_STATUS = Key.create("DELETED_STATUS");

  private WatchForChangesThread myWatchForChangesThread;

  public LocalFileSystemImpl() {
    myCachedRoots = File.listRoots();

    mySynchronizeQueueAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD){
      public void addRequest(Runnable request, int delay) {
        LOG.info("adding request to synchronize queue:" + request);
        super.addRequest(request, delay);
      }
    };

    if (FileWatcher.isAvailable()) {
      FileWatcher.initialize();
      myWatchForChangesThread = new WatchForChangesThread();
      myWatchForChangesThread.start();
      new StoreRefreshStatusThread().start();
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void cleanupForNextTest() {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          refresh(false);
        }
      }
    );

    myRoots.clear();
    myDirtyFiles.clear();
    myDeletedFiles.clear();
  }

  final VirtualFileManagerEx getManager() {
    if (myManager == null) {
      myManager = (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
    }
    
    return myManager;
  }

  private void updateFileWatcher() {
    if (FileWatcher.isAvailable()) {
      FileWatcher.interrupt();
    }
  }

  boolean isRoot(VirtualFileImpl file) {
    synchronized (LOCK) {
      return myRoots.contains(file);
    }
  }

  private boolean isPhysicalRoot(String path) {
    path = path.replace('/', File.separatorChar);
    for (int i = 0; i < myCachedRoots.length; i++) {
      File root = myCachedRoots[i];
      String rootPath = root.getPath();
      if (equal(path, rootPath)) {
        return true;
      }
    }
    return false;
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(String path) {
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }

    VirtualFile file = _findFileByPath(path);
    if (file == null){
      String canonicalPath = getCanonicalPath(new File(path.replace('/', File.separatorChar)));
      if (canonicalPath == null) return null;
      String path1 = canonicalPath.replace(File.separatorChar, '/');
      if (!path.equals(path1)){
        return _findFileByPath(path1);
      }
    }
    return file;
  }

  public VirtualFile _findFileByPath(String path) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return findFileByPath(path, true);
  }

  private VirtualFile findFileByPath(String path, boolean createIfNoCache) {
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      if (path.endsWith(":/")) { // instead of getting canonical path - see below
        path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
      }
    }

    synchronized (LOCK) {
      for (int i = 0; i < myRoots.size(); i++) {
        VirtualFile root = myRoots.get(i);
        String rootPath = root.getPath();
        if (!startsWith(path, rootPath)) continue;
        if (path.length() == rootPath.length()) return root;
        String tail;
        if (path.charAt(rootPath.length()) == '/') {
          tail = path.substring(rootPath.length() + 1);
        }
        else if (StringUtil.endsWithChar(rootPath, '/')) {
          tail = path.substring(rootPath.length());
        }
        else {
          continue;
        }
        StringTokenizer tokenizer = new StringTokenizer(tail, "/");
        while (tokenizer.hasMoreTokens()) {
          final String name = tokenizer.nextToken();
          if (".".equals(name)) continue;
          if ("..".equals(name)) {
            root = root.getParent();
          }
          else {
            if (!createIfNoCache && !((VirtualFileImpl)root).areChildrenCached()) return null;
            if (!root.isDirectory()) return null;
            root = root.findChild(name);
          }
          if (root == null) return null;
        }
        return root;
      }

      boolean isPhysicalRoot = isPhysicalRoot(path);
      File file = new File(path.replace('/', File.separatorChar));
      boolean exists;
      if (!isPhysicalRoot) {
        exists = file.exists();
      }
      else {
        exists = true; // not used
      }
      if (!isPhysicalRoot && exists) { // getting canonicalPath is very slow for some network drives
        String newPath = getCanonicalPath(file);
        if (newPath == null) return null;
        newPath = newPath.replace(File.separatorChar, '/');
        if (!path.equals(newPath)) return findFileByPath(newPath, createIfNoCache);
      }

      if (!createIfNoCache) return null;
      if (!isPhysicalRoot && !exists) return null;

      VirtualFileImpl newRoot = new VirtualFileImpl(this, path);
      for (int i = 0; i < myRoots.size(); i++) {
        VirtualFile root = myRoots.get(i);
        String rootPath = root.getPath();
        if (!startsWith(rootPath, path)) continue;
        if (rootPath.length() == path.length()) return root;
        String tail;
        if (rootPath.charAt(path.length()) == '/') {
          tail = rootPath.substring(path.length() + 1);
        }
        else if (StringUtil.endsWithChar(path, '/')) {
          tail = rootPath.substring(path.length());
        }
        else {
          continue;
        }
        StringTokenizer tokenizer = new StringTokenizer(tail, "/");
        VirtualFileImpl vFile = newRoot;
        while (tokenizer.hasMoreTokens()) {
          String name = tokenizer.nextToken();
          VirtualFile child = vFile.findChild(name);
          if (child == null) break;
          if (!tokenizer.hasMoreTokens()) {
            vFile.replaceChild((VirtualFileImpl)child, (VirtualFileImpl)root);
            ((VirtualFileImpl)root).setParent(vFile);
            myRoots.remove(i);
            i--;
          }
          vFile = (VirtualFileImpl)child;
        }
      }
      myRoots.add(newRoot);
      updateFileWatcher();
      return newRoot;
    }
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    VirtualFile file = findFileByPath(path);
    if (file != null) return file;

    if (!new File(path.replace('/', File.separatorChar)).exists()) return null;

    String parentPath = getParentPath(path);
    if (parentPath == null) return null;
    VirtualFile parent = refreshAndFindFileByPath(parentPath);
    if (parent == null) return null;

    if (FileWatcher.isAvailable()) {
      synchronized (myRefreshStatusMap) { // changes might not be processed yet
        if (myRefreshStatusMap.get(parent) == null) {
          myRefreshStatusMap.put(parent, DIRTY_STATUS);
        }
      }
    }
    parent.refresh(false, false);

    file = findFileByPath(path);
    return file;
  }

  private static String getParentPath(String path) {
    int index = path.lastIndexOf('/');
    if (index < 0) return null;
    if (index == path.length() - 1) return null; // "c:/" or "/"
    path = path.substring(0, index);
    if (path.indexOf('/') < 0) {
      path += "/"; // "c:/" or "/"
    }
    return path;
  }

  public VirtualFile findFileByIoFile(File file) {
    String path = getCanonicalPath(file);
    if (path == null) return null;
    path = path.replace(File.separatorChar, '/');
    return findFileByPath(path);
  }

  public VirtualFile refreshAndFindFileByIoFile(File file) {
    String path = getCanonicalPath(file);
    if (path == null) return null;
    path = path.replace(File.separatorChar, '/');
    return refreshAndFindFileByPath(path);
  }

  public String extractPresentableUrl(String path) {
    String url = super.extractPresentableUrl(path);
    /*
    if (SystemInfo.isWindows || SystemInfo.isOS2){
      if (url.endsWith(":")){
        url += File.separatorChar;
      }
    }
    */
    return url;
  }

  public void refresh(final boolean asynchronous) {
    if (asynchronous) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    else {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    final ModalityState modalityState = EventQueue.isDispatchThread() ? ModalityState.current() : ModalityState.NON_MMODAL;

    final WorkerThread worker;
    if (asynchronous) {
      worker = new WorkerThread("Synchronize worker");
    }
    else {
      worker = null;
    }

    final Runnable endTask = new Runnable() {
      public void run() {
        ProgressIndicator indicator = getManager().getRefreshIndicator();
        if (indicator != null) {
          indicator.stop();
        }

        getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };

    final Runnable runnable = new Runnable() {
      public void run() {
        getManager().beforeRefreshStart(asynchronous, modalityState, null);

        final ProgressIndicator indicator = getManager().getRefreshIndicator();
        if (indicator != null) {
          indicator.start();
          indicator.setText("Synchronizing files...");
        }

        storeRefreshStatusToFiles();

        myCachedRoots = File.listRoots();

        VirtualFileImpl[] roots;
        synchronized (LOCK) {
          roots = myRoots.toArray(new VirtualFileImpl[myRoots.size()]);
        }

        for (int i = 0; i < roots.length; i++) {
          final VirtualFileImpl rootFile = roots[i];
          PhysicalFile file = rootFile.getPhysicalFile();
          if (!file.exists()) {
            final Runnable action = new Runnable() {
              public void run() {
                if (!rootFile.isValid()) return;
                boolean isDirectory = rootFile.isDirectory();
                fireBeforeFileDeletion(null, rootFile);
                synchronized (LOCK) {
                  myRoots.remove(rootFile);
                  updateFileWatcher();
                }
                fireFileDeleted(null, rootFile, rootFile.getName(), isDirectory, null);
              }
            };
            getManager().addEventToFireByRefresh(action, asynchronous, modalityState);
          }
          else {
            refresh(rootFile, true, false, worker, modalityState);
          }
        }
      }
    };

    if (asynchronous) {
      Runnable runnable1 = new Runnable() {
        public void run() {
          LOG.info("Executing request:" + this);

          worker.start();
          ApplicationManager.getApplication().runReadAction(runnable);
          worker.dispose(false);
          try {
            worker.join();
          }
          catch (InterruptedException e) {
          }
          endTask.run();
        }
      };
      getSynchronizeQueueAlarm().addRequest(runnable1, 0);
    }
    else {
      runnable.run();
      endTask.run();
    }
  }

  Alarm getSynchronizeQueueAlarm() {
    return mySynchronizeQueueAlarm;
  }

  void refresh(VirtualFile file, boolean recursive, boolean storeStatus, WorkerThread worker, ModalityState modalityState) {
    if (!FileWatcher.isAvailable()) {
      ((VirtualFileImpl)file).refreshInternal(recursive, worker, modalityState);
    }
    else {
      synchronized (LOCK) {
        for (int i = 0; i < myFilesToWatchManual.size(); i++) {
          VirtualFile fileToWatch = myFilesToWatchManual.get(i);
          if (VfsUtil.isAncestor(fileToWatch, file, false)) {
            ((VirtualFileImpl)file).refreshInternal(recursive, worker, modalityState);
            return;
          }
        }
      }

      if (storeStatus) {
        storeRefreshStatusToFiles();
      }

      Object status;
      synchronized (myRefreshStatusMap) {
        status = myRefreshStatusMap.remove(file);
      }
      if (status == DELETED_STATUS) {
        if (((VirtualFileImpl)file).getPhysicalFile().exists()) { // file was deleted but later restored - need to rescan the whole subtree
          ((VirtualFileImpl)file).refreshInternal(true, worker, modalityState);
        }
      }
      else {
        if (status == DIRTY_STATUS) {
          ((VirtualFileImpl)file).refreshInternal(false, worker, modalityState);
        }
        if (recursive && ((VirtualFileImpl)file).areChildrenCached()) {
          VirtualFile[] children = file.getChildren();
          for (int i = 0; i < children.length; i++) {
            VirtualFile child = children[i];
            if (status == DIRTY_STATUS && !((VirtualFileImpl)child).getPhysicalFile().exists()) continue; // should be already handled above (see SCR6145)
            refresh(child, recursive, false, worker, modalityState);
          }
        }
      }
    }
  }

  private void storeRefreshStatusToFiles() {
    if (FileWatcher.isAvailable()) {
      final String[] dirtyFiles;
      synchronized (myDirtyFiles) {
        dirtyFiles = myDirtyFiles.toArray(new String[myDirtyFiles.size()]);
        myDirtyFiles.clear();
      }
      Runnable action = new Runnable() {
        public void run() {
          for (int i = 0; i < dirtyFiles.length; i++) {
            String path = dirtyFiles[i];
            path = path.replace(File.separatorChar, '/');
            VirtualFile file = findFileByPath(path, false);
            if (file != null) {
              synchronized (myRefreshStatusMap) {
                if (myRefreshStatusMap.get(file) == null) {
                  myRefreshStatusMap.put(file, DIRTY_STATUS);
                }
              }
            }
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action);

      final String[] deletedFiles;
      synchronized (myDeletedFiles) {
        deletedFiles = myDeletedFiles.toArray(new String[myDeletedFiles.size()]);
        myDeletedFiles.clear();
      }
      Runnable action1 = new Runnable() {
        public void run() {
          for (int i = 0; i < deletedFiles.length; i++) {
            String path = deletedFiles[i];
            path = path.replace(File.separatorChar, '/');
            VirtualFile file = findFileByPath(path, false);
            if (file != null) {
              synchronized (myRefreshStatusMap) {
                myRefreshStatusMap.put(file, DELETED_STATUS);
              }
              // when moving file in Explorer FILE_MODIFIED is not fired for the parent...
              VirtualFile parent = file.getParent();
              if (parent != null) {
                synchronized (myRefreshStatusMap) {
                  if (myRefreshStatusMap.get(parent) == null) {
                    myRefreshStatusMap.put(parent, DIRTY_STATUS);
                  }
                }
              }
            }
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action1);
    }
  }

  protected void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  protected void fireFileCreated(Object requestor, VirtualFile file) {
    super.fireFileCreated(requestor, file);
  }

  protected void fireFileDeleted(Object requestor,
                                 VirtualFile file,
                                 String fileName,
                                 boolean isDirectory,
                                 VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, isDirectory, parent);
  }

  protected void fireFileMoved(Object requestor, VirtualFile file, VirtualFile oldParent) {
    super.fireFileMoved(requestor, file, oldParent);
  }

  protected void fireBeforePropertyChange(Object requestor,
                                          VirtualFile file,
                                          String propertyName,
                                          Object oldValue,
                                          Object newValue) {
    super.fireBeforePropertyChange(requestor, file, propertyName, oldValue, newValue);
  }

  protected void firePropertyChanged(Object requestor,
                                     VirtualFile file,
                                     String propertyName,
                                     Object oldValue,
                                     Object newValue) {
    super.firePropertyChanged(requestor, file, propertyName, oldValue, newValue);
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    super.fireBeforeFileDeletion(requestor, file);
  }

  protected void fireBeforeFileMovement(Object requestor, VirtualFile file, VirtualFile newParent) {
    super.fireBeforeFileMovement(requestor, file, newParent);
  }

  private static boolean equal(String path1, String path2) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return path1.equals(path2);
    }
    else {
      return path1.equalsIgnoreCase(path2);
    }
  }

  private static boolean startsWith(String path1, String path2) {
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path1 = path1.toLowerCase();
      path2 = path2.toLowerCase();
    }
    return path1.startsWith(path2);
  }

  private String getCanonicalPath(File file) {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return file.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
    }
    else {
      try {
        return file.getCanonicalPath();
      }
      catch (IOException e) {
        return null;
      }
    }
  }

  private class WatchForChangesThread extends Thread {
    public WatchForChangesThread() {
      super("WatchForChangesThread");
    }

    public void run() {
      updateFileWatcher();
      while (true) {
        FileWatcher.ChangeInfo[] infos = FileWatcher.waitForChange();

        if (infos == null) {
          ApplicationManager.getApplication().runReadAction(
            new Runnable() {
              public void run() {
                synchronized (LOCK) {
                  String[] dirPaths = new String[myRoots.size()];
                  for (int i = 0; i < myRoots.size(); i++) {
                    VirtualFile root = myRoots.get(i);
                    dirPaths[i] = root.getPath();
                  }

                  final Vector<String> watchManual = new Vector<String>();

                  int numDir = FileWatcher.setup(dirPaths, true, watchManual);
                  if (numDir == 0) {
                    try {
                      FileWatcher.setup(new String[]{FileUtil.getTempDirectory()}, true, new Vector());
                    }
                    catch (IOException e) {
                      LOG.error(e);
                    }
                  }

                  myFilesToWatchManual.clear();
                  for (int i = 0; i < watchManual.size(); i++) {
                    String path = watchManual.elementAt(i);
                    path = path.replace(File.separatorChar, '/');
                    VirtualFile file = findFileByPath(path);
                    if (file != null) {
                      myFilesToWatchManual.add(file);
                    }
                  }
                }
              }
            }
          );
        } else {
          for (int i = 0; i < infos.length; i++) {
            FileWatcher.ChangeInfo info = infos[i];
            String path = info.getFilePath();
            int changeType = info.getChangeType();
            if (changeType == FileWatcher.FILE_MODIFIED) {
              synchronized (myDirtyFiles) {
                myDirtyFiles.add(path);
              }
            }
            else if (changeType == FileWatcher.FILE_ADDED) {
              synchronized (myDirtyFiles) {
                String parent = new File(path).getParent();
                if (parent != null) {
                  myDirtyFiles.add(parent);
                }
              }
            }
            else if (changeType == FileWatcher.FILE_REMOVED || changeType == FileWatcher.FILE_RENAMED_OLD_NAME) {
              synchronized (myDeletedFiles) {
                myDeletedFiles.add(path);
              }
            }
          }
        }
      }
    }
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      super("StoreRefreshStatusThread");
      setPriority(MIN_PRIORITY);
    }

    public void run() {
      while (true) {
        storeRefreshStatusToFiles();
        try {
          sleep(PERIOD);
        }
        catch (InterruptedException e) {
        }
      }
    }
  }

  public String getComponentName() {
    return "LocalFileSystem";
  }
}