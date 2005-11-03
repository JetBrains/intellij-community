package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.vfs.local.win32.FileWatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class LocalFileSystemImpl extends LocalFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");
  private VirtualFileManagerEx myManager = null;

  final Object LOCK = new Object();

  private final List<WatchRequest> myRootsToWatch = new ArrayList<WatchRequest>();
  private WatchRequest[] myCachedNormalizedRequests = null;
  private BidirectionalMap<VirtualFile, String> myRootsToPaths = null;

  private ArrayList<VirtualFile> myFilesToWatchManual = new ArrayList<VirtualFile>();
  private final HashSet<String> myDirtyFiles = new HashSet<String>(); // dirty files when FileWatcher is available
  private final HashSet<String> myDeletedFiles = new HashSet<String>();

  private Alarm mySynchronizeQueueAlarm;

  private final Map<VirtualFile,Key> myRefreshStatusMap = new WeakHashMap<VirtualFile, Key>(); // VirtualFile --> 'status'
  private static final Key DIRTY_STATUS = Key.create("DIRTY_STATUS");
  private static final Key DELETED_STATUS = Key.create("DELETED_STATUS");

  private List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();

  private class WatchRequestImpl implements WatchRequest {
    public String myRootPath;

    public String myFSRootPath;
    public boolean myToWatchRecursively;

    public WatchRequestImpl(String rootPath, final boolean toWatchRecursively) {
      myToWatchRecursively = toWatchRecursively;
      final int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (index >= 0) rootPath = rootPath.substring(0, index);
      final File file = new File(rootPath.replace('/', File.separatorChar));
      if (!file.isDirectory()) {
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
          if (SystemInfo.isFileSystemCaseSensitive) {
            myFSRootPath = parentFile.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
          }
          else {
            try {
              myFSRootPath = parentFile.getCanonicalPath();
            }
            catch (IOException e) {
              myFSRootPath = rootPath; //need something
            }
          }
        }
        else {
          myFSRootPath = rootPath.replace('/', File.separatorChar);
        }
        
        myRootPath = myFSRootPath.replace(File.separatorChar, '/');
      } else {
        myRootPath = rootPath.replace(File.separatorChar, '/');
        myFSRootPath = rootPath.replace('/', File.separatorChar);
      }
    }

    @NotNull public String getRootPath() { return myRootPath; }

    @NotNull public String getFileSystemRootPath() { return myFSRootPath; }

    public boolean isToWatchRecursively() { return myToWatchRecursively; }
  }

  public LocalFileSystemImpl() {
    mySynchronizeQueueAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD){
      public void addRequest(Runnable request, int delay) {
        LOG.info("adding request to synchronize queue:" + request);
        super.addRequest(request, delay);
      }
    };

    if (FileWatcher.isAvailable()) {
      FileWatcher.initialize();
      new WatchForChangesThread().start();
      new StoreRefreshStatusThread().start();
    }
  }

  private void initRoots() {
    if (myRootsToPaths != null) return;
    myRootsToPaths = new BidirectionalMap<VirtualFile, String>();

    final File[] files = File.listRoots();
    for (File file : files) {
      String path = getCanonicalPath(file);
      if (path != null) {
        path = path.replace(File.separatorChar, '/');
        final VirtualFileImpl root = new VirtualFileImpl(path);
        myRootsToPaths.put(root, path);
      }
    }
  }

  private void refreshRoots() {
    if (myRootsToPaths == null) {
      initRoots();
    }
    else {
      final File[] files = File.listRoots();
      Set<String> newRootPaths= new HashSet<String>();
      for (File file : files) {
        String path = getCanonicalPath(file);
        if (path != null) {
          path = path.replace(File.separatorChar, '/');
          final List<VirtualFile> roots = myRootsToPaths.getKeysByValue(path);
          if (roots == null) {
            final VirtualFileImpl root = new VirtualFileImpl(path);
            myRootsToPaths.put(root, path);
          }

          newRootPaths.add(path);
        }
      }

      for (Iterator<Map.Entry<VirtualFile,String>> iterator = myRootsToPaths.entrySet().iterator(); iterator.hasNext();) {
        Map.Entry<VirtualFile,String> entry = iterator.next();
        if (!newRootPaths.contains(entry.getValue())) {
          iterator.remove();
        }
      }
    }

  }


  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void cleanupForNextTest() throws IOException {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          refresh(false);
        }
      }
    );

    myRootsToWatch.clear();
    myRootsToPaths = null;
    myDirtyFiles.clear();
    myDeletedFiles.clear();

    final File file = new File(FileUtil.getTempDirectory());
    String path = file.getCanonicalPath().replace(File.separatorChar, '/');
    addRootToWatch(path, true);
  }

  final VirtualFileManagerEx getManager() {
    if (myManager == null) {
      myManager = (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
    }

    return myManager;
  }

  private void updateFileWatcher() {
    if (FileWatcher.isAvailable()) {
      FileWatcher.interruptWatcher();
    }
  }

  boolean isRoot(VirtualFileImpl file) {
    synchronized (LOCK) {
      initRoots();
      return myRootsToPaths.containsKey(file);
    }
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
      initRoots();
      for (VirtualFile root : myRootsToPaths.keySet()) {
        String rootPath = root.getPath();
        if (!FileUtil.startsWith(path, rootPath)) continue;
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
            root = root.findChild(name);
          }
          if (root == null) return null;
        }
        return root;
      }

      return null;
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
    return super.extractPresentableUrl(path);
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
          indicator.setText(VfsBundle.message("file.synchronize.progress"));
        }

        storeRefreshStatusToFiles();

        refreshRoots();
        WatchRequest[] requests;
        synchronized (LOCK) {
          requests = normalizeRootsForRefresh();
        }

        for (final WatchRequest request : requests) {
          final String rootPath = request.getRootPath();

          String runPath = rootPath;
          final VirtualFileImpl rootFile = (VirtualFileImpl)findFileByPath(runPath);
          if (rootFile != null) {

            final PhysicalFile file = rootFile.getPhysicalFile();
            final boolean recursively = request.isToWatchRecursively();

            if (!file.exists()) {
              final Runnable action = new Runnable() {
                public void run() {
                  if (!rootFile.isValid()) return;
                  boolean isDirectory = rootFile.isDirectory();
                  fireBeforeFileDeletion(null, rootFile);
                  synchronized (LOCK) {
                    final VirtualFileImpl parent = (VirtualFileImpl)rootFile.getParent();
                    if (parent != null) parent.removeChild(rootFile);
                    myRootsToWatch.remove(request);
                    updateFileWatcher();
                  }
                  fireFileDeleted(null, rootFile, rootFile.getName(), isDirectory, null);
                }
              };
              getManager().addEventToFireByRefresh(action, asynchronous, modalityState);
            }
            else {
              refresh(rootFile, recursively, false, worker, modalityState, asynchronous, true);
            }
          } else {
            final boolean physicalExists = new File(request.getFileSystemRootPath()).exists();
            if (physicalExists) {
              final int index = runPath.lastIndexOf('/');
              while(index >= 0) {
                String parentPath = runPath.substring(0, index);
                final VirtualFileImpl vParent = (VirtualFileImpl)findFileByPath(parentPath);
                if (vParent != null) {
                  final String path = runPath;
                  getManager().addEventToFireByRefresh(new Runnable() {
                    public void run() {
                      final VirtualFileImpl newVFile = new VirtualFileImpl(path);
                      vParent.addChild(newVFile);
                      newVFile.setParent(vParent);
                      fireFileCreated(null, newVFile);
                    }
                  }, asynchronous, modalityState);
                  break;
                }

                runPath = parentPath;
              }
            }
          }
        }

        FileWatcher.resyncedManually();
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

  private WatchRequest[] normalizeRootsForRefresh() {
    if (myCachedNormalizedRequests != null) return myCachedNormalizedRequests;
    List<WatchRequest> result = new ArrayList<WatchRequest>();
    synchronized (LOCK) {
      NextRoot:
      for (WatchRequest request : myRootsToWatch) {
        String rootPath = request.getRootPath();
        boolean recursively = request.isToWatchRecursively();

        for (Iterator<WatchRequest> iterator1 = result.iterator(); iterator1.hasNext();) {
          final WatchRequest otherRequest = iterator1.next();
          final String otherRootPath = otherRequest.getRootPath();
          final boolean otherRecursively = otherRequest.isToWatchRecursively();
          if ((rootPath.equals(otherRootPath) && (!recursively || otherRecursively)) ||
              (FileUtil.startsWith(rootPath, otherRootPath) && otherRecursively)) {
            continue NextRoot;
          }
          else if (FileUtil.startsWith(otherRootPath, rootPath) && (recursively || !otherRecursively)) {
            iterator1.remove();
          }
        }
        result.add(request);
      }
    }

    myCachedNormalizedRequests = result.toArray(new WatchRequest[result.size()]);
    return myCachedNormalizedRequests;
  }

  Alarm getSynchronizeQueueAlarm() {
    return mySynchronizeQueueAlarm;
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull final VirtualFile... files) {
    if (files.length == 0) return;
    final WorkerThread worker = asynchronous ? new WorkerThread("refresh virtual files") : null;
    final Runnable runnable = new Runnable() {
      public void run() {
        final ModalityState modalityState = EventQueue.isDispatchThread() ? ModalityState.current() : ModalityState.NON_MMODAL;
        getManager().beforeRefreshStart(asynchronous, modalityState, EmptyRunnable.getInstance());

        for (VirtualFile file : files) {
          LOG.assertTrue(!file.isDirectory());
          ((VirtualFileImpl)file).refreshInternal(false, worker, modalityState, true);
        }

        getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };
    if (asynchronous) {
      Runnable runnable1 = new Runnable() {
        public void run() {
          worker.start();
          ApplicationManager.getApplication().runReadAction(runnable);
          worker.dispose(false);
          try {
            worker.join();
          }
          catch (InterruptedException e) {
          }
        }
      };
      getSynchronizeQueueAlarm().addRequest(runnable1, 0);
    }
    else {
      runnable.run();
    }
  }

  void refresh(VirtualFile file,
               boolean recursive,
               boolean storeStatus,
               WorkerThread worker,
               ModalityState modalityState,
               boolean asynchronous, final boolean isRoot) {
    if (!FileWatcher.isAvailable() || !recursive && !asynchronous) { // We're unable to definitely refresh syncronously by means of file watcher.
      ((VirtualFileImpl)file).refreshInternal(recursive, worker, modalityState, false);
      if (!recursive && isRoot && ((VirtualFileImpl)file).areChildrenCached()) {
        final VirtualFile[] children = file.getChildren();
        for (VirtualFile child : children) {
          ((VirtualFileImpl)child).refreshInternal(false, worker, modalityState, false);
        }
      }
    }
    else {
      synchronized (LOCK) {
        for (VirtualFile fileToWatch : myFilesToWatchManual) {
          if (VfsUtil.isAncestor(fileToWatch, file, false)) {
            ((VirtualFileImpl)file).refreshInternal(recursive, worker, modalityState, false);
            if (isRoot && !recursive && ((VirtualFileImpl) file).areChildrenCached()) {
              VirtualFile[] children = file.getChildren();
              for (VirtualFile child : children) {
                ((VirtualFileImpl)child).refreshInternal(false, worker, modalityState, false);
              }
            }
            return;
          }
        }
      }

      if (storeStatus) {
        storeRefreshStatusToFiles();
      }

      Key status;
      synchronized (myRefreshStatusMap) {
        status = myRefreshStatusMap.remove(file);
      }
      if (status == DELETED_STATUS) {
        if (((VirtualFileImpl)file).getPhysicalFile().exists()) { // file was deleted but later restored - need to rescan the whole subtree
          ((VirtualFileImpl)file).refreshInternal(true, worker, modalityState, false);
        }
      }
      else {
        if (status == DIRTY_STATUS) {
          ((VirtualFileImpl)file).refreshInternal(false, worker, modalityState, false);
        }
        if ((isRoot || recursive) && ((VirtualFileImpl)file).areChildrenCached()) {
          VirtualFile[] children = file.getChildren();
          for (VirtualFile child : children) {
            if (status == DIRTY_STATUS &&
                !((VirtualFileImpl)child).getPhysicalFile().exists()) {
              continue; // should be already handled above (see SCR6145)
            }
            refresh(child, recursive, false, worker, modalityState, asynchronous, false);
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
          for (String path : dirtyFiles) {
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
          for (String path : deletedFiles) {
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
      //noinspection HardCodedStringLiteral
      super("WatchForChangesThread");
    }

    public void run() {
      updateFileWatcher();
      while (true) {
        FileWatcher.ChangeInfo[] infos = FileWatcher.waitForChange();

        if (infos == null) {
          setUpFileWatcher();
        } else {
          for (FileWatcher.ChangeInfo info : infos) {
            if (info == null) continue;

            String path = info.getFilePath();
            int changeType = info.getChangeType();
            if (changeType == FileWatcher.FILE_MODIFIED) {
              synchronized (myDirtyFiles) {
                myDirtyFiles.add(path);
              }
            }
            else if (changeType == FileWatcher.FILE_ADDED || changeType == FileWatcher.FILE_RENAMED_NEW_NAME) {
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

  private void setUpFileWatcher() {
    if (FileWatcher.isAvailable()) {
      ApplicationManager.getApplication().runReadAction(
        new Runnable() {
          public void run() {
            synchronized (LOCK) {
              final WatchRequest[] watchRequests = normalizeRootsForRefresh();
              String[] dirPaths = new String[watchRequests.length];
              boolean[] toWatchRecursively = new boolean[watchRequests.length];
              int cnt = 0;
              for (WatchRequest root : watchRequests) {
                dirPaths[cnt] = root.getFileSystemRootPath();
                toWatchRecursively[cnt] = root.isToWatchRecursively();
                cnt++;
              }

              final Vector<String> watchManual = new Vector<String>();

              int numDir = FileWatcher.setup(dirPaths, toWatchRecursively, watchManual);
              if (numDir == 0) {
                try {
                  FileWatcher.setup(new String[]{FileUtil.getTempDirectory()}, new boolean[] {false}, new Vector());
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
    }

    myCachedNormalizedRequests = null;
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      //noinspection HardCodedStringLiteral
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

  public WatchRequest addRootToWatch(String rootPath, boolean toWatchRecursively) {
    LOG.assertTrue(rootPath != null);
    synchronized (LOCK) {
      final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
      myRootsToWatch.add(result);
      setUpFileWatcher();
      return result;
    }
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(final Collection<String> rootPaths, final boolean toWatchRecursively) {
    LOG.assertTrue(rootPaths != null);
    Set<WatchRequest> result = new HashSet<WatchRequest>();
    synchronized (LOCK) {
      for (String rootPath : rootPaths) {
        LOG.assertTrue(rootPath != null);
        final WatchRequestImpl request = new WatchRequestImpl(rootPath, toWatchRecursively);
        result.add(request);
      }

      myRootsToWatch.addAll(result);
      setUpFileWatcher();
    }

    return result;
  }

  public void removeWatchedRoot(final WatchRequest watchRequest) {
    synchronized (LOCK) {
      if (myRootsToWatch.remove(watchRequest)) setUpFileWatcher();
    }
  }

  public void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    LOG.assertTrue(!myHandlers.contains(handler), "Handler " + handler + " already registered.");
    myHandlers.add(handler);
  }

  public void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    LOG.assertTrue(myHandlers.remove(handler), "Handler" + handler + " haven't been registered or already unregistered.");
  }

  public boolean auxDelete(VirtualFile file) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.delete(file)) return true;
    }

    return false;
  }

  public boolean auxMove(VirtualFile file, VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  public boolean auxRename(VirtualFile file, String newName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  public boolean auxCreateFile(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  public boolean auxCreateDirectory(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  public void removeWatchedRoots(final Collection<WatchRequest> rootsToWatch) {
    synchronized (LOCK) {
      if (myRootsToWatch.removeAll(rootsToWatch)) setUpFileWatcher();
    }
  }

  public void forceRefreshFile(VirtualFile file) {
    forceRefreshFiles(false, file);
  }
}
