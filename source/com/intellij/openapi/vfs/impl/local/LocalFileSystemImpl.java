package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.intellij.vfs.local.win32.FileWatcher;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class LocalFileSystemImpl extends LocalFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");
  private VirtualFileManagerEx myManager = null;

  final ReadWriteLock LOCK = new ReentrantReadWriteLock();
  final Lock READ_LOCK = LOCK.readLock();
  final Lock WRITE_LOCK = LOCK.writeLock();

  private final List<WatchRequest> myRootsToWatch = new ArrayList<WatchRequest>();
  private WatchRequest[] myCachedNormalizedRequests = null;
  private BidirectionalMap<VirtualFile, String> myFSRootsToPaths = null;

  private List<String> myFilePathsToWatchManual = new ArrayList<String>();
  private final Set<String> myDirtyFiles = new HashSet<String>(); // dirty files when FileWatcher is available
  private final Set<String> myDeletedFiles = new HashSet<String>();

  private final ExecutorService mySynchronizeExecutor = ConcurrencyUtil.newSingleThreadExecutor("File System Synchronize Executor");
  private List<Future<?>> myFutures;

  private final Map<VirtualFile, Key> myRefreshStatusMap = new WeakHashMap<VirtualFile, Key>(); // VirtualFile --> 'status'
  private static final Key DIRTY_STATUS = Key.create("DIRTY_STATUS");
  private static final Key DELETED_STATUS = Key.create("DELETED_STATUS");

  private List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();
  public final Map<String, VirtualFileImpl> myUnaccountedFiles = SystemInfo.isFileSystemCaseSensitive
                                                                 ? new THashMap<String, VirtualFileImpl>()
                                                                 : new THashMap<String, VirtualFileImpl>(
                                                                   new CaseInsensitiveStringHashingStrategy());

  private static class WatchRequestImpl implements WatchRequest {
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
      }
      else {
        myRootPath = rootPath.replace(File.separatorChar, '/');
        myFSRootPath = rootPath.replace('/', File.separatorChar);
      }
    }

    @NotNull
    public String getRootPath() {
      return myRootPath;
    }

    @NotNull
    public String getFileSystemRootPath() {
      return myFSRootPath;
    }

    public boolean isToWatchRecursively() {
      return myToWatchRecursively;
    }

    public boolean dominates(WatchRequest other) {
      if (myToWatchRecursively) {
        return other.getRootPath().startsWith(myRootPath);
      }

      return !other.isToWatchRecursively() && myRootPath.equals(other.getRootPath());
    }
  }

  public LocalFileSystemImpl() {
    if (FileWatcher.isAvailable()) {
      FileWatcher.initialize();
      new WatchForChangesThread().start();
      new StoreRefreshStatusThread().start();
    }
  }

  private void initRoots() {
    if (myFSRootsToPaths != null) return;

    WRITE_LOCK.lock();
    try {
      final BidirectionalMap<VirtualFile, String> map = new BidirectionalMap<VirtualFile, String>();

      final File[] files = File.listRoots();
      for (File file : files) {
        String path = file.getPath();
        if (path != null) {
          path = path.replace(File.separatorChar, '/');
          final VirtualFileImpl root = new VirtualFileImpl(path);
          map.put(root, path);
        }
      }
      myFSRootsToPaths = map;
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  private void refreshFSRoots() {
    if (myFSRootsToPaths == null) {
      initRoots();
    }
    else {
      final File[] files = File.listRoots();

      WRITE_LOCK.lock();
      try {
        Set<String> newRootPaths = new HashSet<String>();
        for (File file : files) {
          String path = file.getPath();
          if (path != null) {
            path = path.replace(File.separatorChar, '/');
            final List<VirtualFile> roots = myFSRootsToPaths.getKeysByValue(path);
            if (roots == null) {
              final VirtualFileImpl root = new VirtualFileImpl(path);
              myFSRootsToPaths.put(root, path);
            }

            newRootPaths.add(path);
          }
        }

        for (Iterator<Map.Entry<VirtualFile, String>> iterator = myFSRootsToPaths.entrySet().iterator(); iterator.hasNext();) {
          Map.Entry<VirtualFile, String> entry = iterator.next();
          if (!newRootPaths.contains(entry.getValue())) {
            iterator.remove();
          }
        }
      }
      finally {
        WRITE_LOCK.unlock();
      }
    }
  }


  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void cleanupForNextTest() throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        refresh(false);
      }
    });

    myRootsToWatch.clear();
    myUnaccountedFiles.clear();
    myFSRootsToPaths = null;
    myDirtyFiles.clear();
    myDeletedFiles.clear();

    final File file = new File(FileUtil.getTempDirectory());
    String path = file.getCanonicalPath().replace(File.separatorChar, '/');
    addRootToWatch(path, true);
  }

  final VirtualFileManagerEx getManager() {
    if (myManager == null) {
      myManager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    }

    return myManager;
  }

  private static void updateFileWatcher() {
    if (FileWatcher.isAvailable()) {
      FileWatcher.interruptWatcher();
    }
  }

  boolean isRoot(VirtualFileImpl file) {
    READ_LOCK.lock();
    try {
      initRoots();
      return myFSRootsToPaths.containsKey(file);
    }
    finally {
      READ_LOCK.unlock();
    }
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull String path) {
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }

    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return findFileByPath(canonicalPath, true, false);
  }

  private VirtualFile findFileByPath(String path, boolean createIfNoCache, final boolean refreshIfNotFound) {
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      if (path.endsWith(":/")) { // instead of getting canonical path - see below
        path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
      }
    }

    initRoots();
    for (VirtualFile root : myFSRootsToPaths.keySet()) {
      //noinspection NonConstantStringShouldBeStringBuffer
      String runPath = root.getPath();
      if (runPath.endsWith("/")) runPath = runPath.substring(0, runPath.length() - 1);
      if (!FileUtil.startsWith(path, runPath)) continue;
      if (path.length() == runPath.length()) return root;
      String tail;
      if (path.charAt(runPath.length()) == '/') {
        tail = path.substring(runPath.length() + 1);
      }
      else if (StringUtil.endsWithChar(runPath, '/')) {
        tail = path.substring(runPath.length());
      }
      else {
        continue;
      }
      StringTokenizer tokenizer = new StringTokenizer(tail, "/");
      while (tokenizer.hasMoreTokens()) {
        final String name = tokenizer.nextToken();
        if (".".equals(name)) continue;
        if ("..".equals(name)) {
          final int index = runPath.lastIndexOf("/");
          if (index >= 0) {
            runPath = runPath.substring(0, index);
          }
          else {
            return null;
          }
          root = root.getParent();
          if (root == null) return null;
        }
        else {
          runPath = runPath + "/" + name;
          if (!((VirtualFileImpl)root).areChildrenCached()) {
            VirtualFile child = myUnaccountedFiles.get(runPath);
            if (child == null || !child.isValid()) {
              if (!createIfNoCache) return null;
              if (myUnaccountedFiles.containsKey(runPath)) {
                if (refreshIfNotFound) {
                  root.refresh(false, false);
                  child = ((VirtualFileImpl)root).findSingleChild(name, true);
                  if (child == null) return null;
                  //need to fire event here since refresh did not fire, because children are not cached
                  fireFileCreated(null, child);
                  root = child;
                }
                else {
                  return null;
                }
              }
              else {
                root = ((VirtualFileImpl)root).findSingleChild(name, true);
                if (root == null) return null;
              }
            }
            else {
              root = child;
            }
          }
          else {
            VirtualFile child = root.findChild(name);
            if (child == null) {
              if (refreshIfNotFound) {
                root.refresh(false, false);
                child = root.findChild(name);
                if (child == null) return null;
              }
              else {
                return null;
              }
            }
            root = child;
          }
        }
      }

      return root;
    }

    return null;
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(String path) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return findFileByPath(canonicalPath, true, true);
  }

  public VirtualFile findFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile findFileByIoFile(final IFile file) {
    String path = file.getPath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  public VirtualFile refreshAndFindFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    String path = ioFile.getPath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  public void refreshIoFiles(Iterable<File> files) {
    final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(false);
    myManager.beforeRefreshStart(false, modalityState, null);
    for (File file : files) {
      final VirtualFile virtualFile = refreshAndFindFileByIoFile(file);
      if (virtualFile != null) virtualFile.refresh(false, false);
    }
    myManager.afterRefreshFinish(false, modalityState);
  }

  public void refreshFiles(Iterable<VirtualFile> files) {
    final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(false);
    myManager.beforeRefreshStart(false, modalityState, null);
    for (VirtualFile file : files) {
      file.refresh(false, false);
    }
    myManager.afterRefreshFinish(false, modalityState);
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    if (!(virtualFile instanceof VirtualFileImpl)) return virtualFile.contentsToByteArray();
    VirtualFileImpl virtualFileImpl = (VirtualFileImpl)virtualFile;
    InputStream inputStream = virtualFileImpl.getPhysicalFileInputStream();
    try {
      int physicalFileLength = virtualFileImpl.getPhysicalFileLength();
      LOG.assertTrue(physicalFileLength >= 0);
      return FileUtil.loadBytes(inputStream, physicalFileLength);
    }
    finally {
      inputStream.close();
    }
  }

  public long physicalLength(final VirtualFile virtualFile) throws IOException {
    if (virtualFile instanceof VirtualFileImpl) {
      return ((VirtualFileImpl)virtualFile).getPhysicalFileLength();
    }
    return virtualFile.getLength();
  }

  public String extractPresentableUrl(String path) {
    return super.extractPresentableUrl(path);
  }

  public void refresh(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(asynchronous);

    final Runnable runnable = new Runnable() {
      public void run() {
        getManager().beforeRefreshStart(asynchronous, modalityState, null);

        storeRefreshStatusToFiles();

        refreshFSRoots();
        WatchRequest[] requests;
        WRITE_LOCK.lock();
        try {
          requests = normalizeRootsForRefresh();
        }
        finally {
          WRITE_LOCK.unlock();
        }

        for (final WatchRequest request : requests) {
          String runPath = request.getRootPath();
          final VirtualFileImpl rootFile = (VirtualFileImpl)findFileByPath(runPath, true, false);
          if (rootFile != null) {

            final File file = rootFile.getPhysicalFile();
            final boolean recursively = request.isToWatchRecursively();

            if (!file.exists()) {
              final Runnable action = new Runnable() {
                public void run() {
                  if (!rootFile.isValid()) return;
                  fireBeforeFileDeletion(null, rootFile);
                  WRITE_LOCK.lock();
                  try {
                    final VirtualFileImpl parent = rootFile.getParent();
                    if (parent != null) parent.removeChild(rootFile);
                  }
                  finally {
                    WRITE_LOCK.unlock();
                  }
                  fireFileDeleted(null, rootFile, rootFile.getName(), null);
                }
              };
              getManager().addEventToFireByRefresh(action, asynchronous, modalityState);
            }
            else {
              refresh(rootFile, recursively, false, modalityState, asynchronous, false);
              if (!recursively && rootFile.areChildrenCached()) {
                for (VirtualFileImpl child : rootFile.getChildren()) {
                  refreshInner(child, false, modalityState, asynchronous, false);
                }
              }
            }
          }
          else {
            final String fileSystemPath = request.getFileSystemRootPath();
            checkFileCreated(fileSystemPath, runPath, asynchronous, modalityState);
          }
        }

        final Set<Map.Entry<String, VirtualFileImpl>> entries =
          new HashSet<Map.Entry<String, VirtualFileImpl>>(myUnaccountedFiles.entrySet());
        for (final Map.Entry<String, VirtualFileImpl> entry : entries) {
          final VirtualFileImpl file = entry.getValue();
          if (file != null && file.isValid()) {
            if (!file.getPhysicalFile().exists()) {
              final Runnable action = new Runnable() {
                public void run() {
                  if (!file.isValid()) return;
                  fireBeforeFileDeletion(null, file);
                  WRITE_LOCK.lock();
                  try {
                    myUnaccountedFiles.put(entry.getKey(), null);
                    file.setParent(null);
                  }
                  finally {
                    WRITE_LOCK.unlock();
                  }
                  fireFileDeleted(null, file, file.getName(), null);
                }
              };
              getManager().addEventToFireByRefresh(action, asynchronous, modalityState);

            }
            else {
              refresh(file, true, false, modalityState, asynchronous, false);
            }
          }
          else {
            final String vfsPath = entry.getKey();
            final String fsPath = vfsPath.replace('/', File.separatorChar);
            checkFileCreated(fsPath, vfsPath, asynchronous, modalityState);
          }
        }

        FileWatcher.resyncedManually();

        getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };

    if (asynchronous) {
      submitAsynchronousTask(new Runnable() {
        public void run() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Executing request:" + this);
          }
          runnable.run();
        }
      });
    }
    else {
      runnable.run();
    }
  }

  private void checkFileCreated(final String fileSystemPath,
                                String vfsPath,
                                final boolean asynchronous,
                                final ModalityState modalityState) {
    final boolean physicalExists = new File(fileSystemPath).exists();
    if (physicalExists) {
      int index = vfsPath.lastIndexOf('/');
      while (index >= 0) {
        String parentPath = vfsPath.substring(0, index);
        final VirtualFileImpl vParent = (VirtualFileImpl)findFileByPath(parentPath, true, false);
        if (vParent != null) {
          final String path = vfsPath;
          getManager().addEventToFireByRefresh(new Runnable() {
            public void run() {
              if (vParent.findSingleChild(new File(path).getName(), false) != null) return;
              final VirtualFileImpl newVFile = new VirtualFileImpl(path);
              vParent.addChild(newVFile);
              fireFileCreated(null, newVFile);
            }
          }, asynchronous, modalityState);
          break;
        }

        vfsPath = parentPath;
        index = vfsPath.lastIndexOf('/');
      }
    }
  }

  private WatchRequest[] normalizeRootsForRefresh() {
    if (myCachedNormalizedRequests != null) return myCachedNormalizedRequests;
    List<WatchRequest> result = new ArrayList<WatchRequest>();
    WRITE_LOCK.lock();
    try {
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
    finally {
      WRITE_LOCK.unlock();
    }

    myCachedNormalizedRequests = result.toArray(new WatchRequest[result.size()]);
    return myCachedNormalizedRequests;
  }

  ExecutorService getSynchronizeExecutor() {
    return mySynchronizeExecutor;
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull final VirtualFile... files) {
    if (files.length == 0) return;
    final Runnable runnable = new Runnable() {
      public void run() {
        final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(asynchronous);
        getManager().beforeRefreshStart(asynchronous, modalityState, null);

        for (VirtualFile file : files) {
          LOG.assertTrue(!file.isDirectory());
          ((VirtualFileImpl)file).refreshInternal(false, modalityState, true, asynchronous, false);
        }

        getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };
    if (asynchronous) {
      submitAsynchronousTask(runnable);
    }
    else {
      runnable.run();
    }
  }

  void submitAsynchronousTask(final Runnable task) {
    Future<?> future = mySynchronizeExecutor.submit(task);
    if (myFutures != null) {
      myFutures.add(future);
    }
  }

  //for use in tests only
  public void startAsynchronousTasksMonitoring() {
    myFutures = new ArrayList<Future<?>>();
  }

  //for use in tests only
  public void waitForAsynchronousTasksCompletion() {
    LOG.assertTrue(myFutures != null, "tasks monitoring should have been started");

    for (Future<?> future : myFutures) {
      try {
        future.get();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }

      myFutures = null;
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  void refresh(VirtualFileImpl file,
               boolean recursive,
               boolean storeStatus,
               ModalityState modalityState,
               boolean asynchronous,
               final boolean noWatcher) {
    final List<String> manualPaths;
    READ_LOCK.lock();
    try {
      manualPaths = new ArrayList<String>(myFilePathsToWatchManual);
    }
    finally {
      READ_LOCK.unlock();
    }

    if (!manualPaths.isEmpty()) {
      final String filePath = file.getPath();
      for (int i = 0; i < manualPaths.size(); i++) {
        String pathToWatchManual = manualPaths.get(i);
        if (FileUtil.startsWith(filePath, pathToWatchManual)) {
          file.refreshInternal(recursive, modalityState, false, asynchronous, noWatcher);
          return;
        }
      }
    }

    if (storeStatus) {
      storeRefreshStatusToFiles();
    }

    refreshInner(file, recursive, modalityState, asynchronous, noWatcher);
  }

  void refreshInner(VirtualFileImpl file, boolean recursive, ModalityState modalityState, boolean asynchronous, final boolean noWatcher) {
    if (noWatcher || !FileWatcher.isAvailable() ||
        !recursive && !asynchronous) { // We're unable to definitely refresh synchronously by means of file watcher.
      file.refreshInternal(recursive, modalityState, false, asynchronous, noWatcher);
    }
    else {
      Key status;
      synchronized (myRefreshStatusMap) {
        status = myRefreshStatusMap.remove(file);
      }
      if (status == DELETED_STATUS) {
        if (file.getPhysicalFile().exists()) { // file was deleted but later restored - need to rescan the whole subtree
          file.refreshInternal(true, modalityState, false, asynchronous, noWatcher);
        }
      }
      else {
        if (status == DIRTY_STATUS) {
          file.refreshInternal(false, modalityState, false, asynchronous, noWatcher);
        }
        if (recursive &&
            file.areChildrenCached()) { //here above refresh was not recursive, so in case of recursive we need to trigger it here
          for (VirtualFileImpl child : file.getChildren()) {
            if (status == DIRTY_STATUS && !child.getPhysicalFile().exists()) {
              continue; // should be already handled above (see SCR6145)
            }
            refreshInner(child, recursive, modalityState, asynchronous, noWatcher);
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
      for (String dirtyFile : dirtyFiles) {
        String path = dirtyFile.replace(File.separatorChar, '/');
        VirtualFile file = findFileByPath(path, false, false);
        if (file != null) {
          synchronized (myRefreshStatusMap) {
            if (myRefreshStatusMap.get(file) == null) {
              myRefreshStatusMap.put(file, DIRTY_STATUS);
            }
          }
        }
      }

      final String[] deletedFiles;
      synchronized (myDeletedFiles) {
        deletedFiles = myDeletedFiles.toArray(new String[myDeletedFiles.size()]);
        myDeletedFiles.clear();
      }
      for (String deletedFile : deletedFiles) {
        String path = deletedFile.replace(File.separatorChar, '/');
        VirtualFile file = findFileByPath(path, false, false);
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
  }

  protected void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  protected void fireFileCreated(Object requestor, VirtualFile file) {
    super.fireFileCreated(requestor, file);
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, parent);
  }

  protected void fireBeforePropertyChange(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    super.fireBeforePropertyChange(requestor, file, propertyName, oldValue, newValue);
  }

  protected void firePropertyChanged(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue) {
    super.firePropertyChanged(requestor, file, propertyName, oldValue, newValue);
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    super.fireBeforeFileDeletion(requestor, file);
  }

  @Nullable
  private static String getVfsCanonicalPath(String path) {
    if (path.length() == 0) return path;
    if (SystemInfo.isWindows) {
      if (path.charAt(0) == '/') path = path.substring(1); //hack over new File(path).toUrl().getFile()
      if (path.contains("~")) {
        try {
          return new File(path.replace('/', File.separatorChar)).getCanonicalPath().replace(File.separatorChar, '/');
        }
        catch (IOException e) {
          return null;
        }
      }
    }
    return path.replace(File.separatorChar, '/');
  }

  private class WatchForChangesThread extends Thread {
    public WatchForChangesThread() {
      //noinspection HardCodedStringLiteral
      super("WatchForChangesThread");
    }

    public void run() {
      updateFileWatcher();
      try {
        //noinspection InfiniteLoopStatement
        while (true) {
          FileWatcher.ChangeInfo[] infos = FileWatcher.waitForChange();

          if (infos == null) {
            setUpFileWatcher();
          }
          else {
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
      catch (IOException e) {
        LOG.info("Watcher terminated and attempt to restart has failed. Exiting watching thread.", e);
      }
    }
  }

  private void setUpFileWatcher() {
    if (FileWatcher.isAvailable()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          WRITE_LOCK.lock();
          try {
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
            FileWatcher.setup(dirPaths, toWatchRecursively, watchManual);
/*
            if (numDir == 0) {
              FileWatcher.setup(new String[]{PathManager.getBinPath()}, new boolean[] {false}, new Vector());
            }
            */
            myFilePathsToWatchManual.clear();
            for (int i = 0; i < watchManual.size(); i++) {
              String path = watchManual.elementAt(i);
              path = path.replace(File.separatorChar, '/');
              myFilePathsToWatchManual.add(path);
            }
          }
          finally {
            WRITE_LOCK.unlock();
          }
        }
      });
    }
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      //noinspection HardCodedStringLiteral
      super("StoreRefreshStatusThread");
      setPriority(MIN_PRIORITY);
      setDaemon(true);
    }

    public void run() {
      //noinspection InfiniteLoopStatement
      while (true) {
        storeRefreshStatusToFiles();
        try {
          sleep(PERIOD);
        }
        catch (InterruptedException e) {
          //normal situation
        }
      }
    }
  }

  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  public WatchRequest addRootToWatch(@NotNull String rootPath, boolean toWatchRecursively) {
    WRITE_LOCK.lock();
    try {
      final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
      final VirtualFile existingFile = findFileByPath(rootPath, false, false);
      if (existingFile != null) {
        if (!isAlreadyWatched(result)) {
          synchronizeFiles(toWatchRecursively, existingFile);
        }
      }
      myRootsToWatch.add(result);
      myCachedNormalizedRequests = null;
      setUpFileWatcher();
      return result;
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  private boolean isAlreadyWatched(final WatchRequest request) {
    for (final WatchRequest current : normalizeRootsForRefresh()) {
      if (current.dominates(request)) return true;
    }
    return false;
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(final @NotNull Collection<String> rootPaths, final boolean toWatchRecursively) {
    Set<WatchRequest> result = new HashSet<WatchRequest>();
    Set<VirtualFile> filesToSynchronize = new HashSet<VirtualFile>();

    WRITE_LOCK.lock();
    try {
      for (String rootPath : rootPaths) {
        LOG.assertTrue(rootPath != null);
        final WatchRequestImpl request = new WatchRequestImpl(rootPath, toWatchRecursively);
        final VirtualFile existingFile = findFileByPath(rootPath, false, false);
        if (existingFile != null) {
          if (!isAlreadyWatched(request)) {
            filesToSynchronize.add(existingFile);
          }
        }
        result.add(request);
        myRootsToWatch.add(request); //add in any case, safe to add inplace without copying myRootsToWatch before the loop
      }
      myCachedNormalizedRequests = null;
      setUpFileWatcher();
      if (!filesToSynchronize.isEmpty()) {
        synchronizeFiles(toWatchRecursively, filesToSynchronize.toArray(new VirtualFile[filesToSynchronize.size()]));
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }

    return result;
  }

  private void synchronizeFiles(final boolean recursively, final VirtualFile... files) {
    for (final VirtualFile file : files) {
      ((VirtualFileImpl)file).refresh(true, recursively, true, null);
      if (!recursively) {
        if (((VirtualFileImpl)file).areChildrenCached()) {
          for (final VirtualFile child : file.getChildren()) {
            ((VirtualFileImpl)child).refresh(true, false, true, null);
          }
        }
        else {
          for (final VirtualFileImpl unaccounted : myUnaccountedFiles.values()) {
            if (unaccounted != null && file.equals(unaccounted.getParent())) {
              unaccounted.refresh(true, false, true, null);
            }
          }
        }
      }
    }
  }

  public void removeWatchedRoot(final WatchRequest watchRequest) {
    WRITE_LOCK.lock();
    try {
      if (myRootsToWatch.remove(watchRequest)) {
        myCachedNormalizedRequests = null;
        setUpFileWatcher();
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  public void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (myHandlers.contains(handler)) {
      LOG.error("Handler " + handler + " already registered.");
    }
    myHandlers.add(handler);
  }

  public void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (!myHandlers.remove(handler)) {
      LOG.error("Handler" + handler + " haven't been registered or already unregistered.");
    }
  }

  public boolean processCachedFilesInSubtree(final VirtualFile file, Processor<VirtualFile> processor) {
    if (file.getFileSystem() != this) return true;

    if (!processFile(file, processor)) return false;

    if (file.isDirectory()) {
      final Collection<VirtualFile> unaccountedFiles = new HashSet<VirtualFile>(myUnaccountedFiles.values());
      for (final VirtualFile unaccounted : unaccountedFiles) {
        if (unaccounted != null && VfsUtil.isAncestor(file, unaccounted, true)) {
          if (!processFile(unaccounted, processor)) return false;
        }
      }
    }

    return true;
  }

  private static boolean processFile(VirtualFile file, Processor<VirtualFile> processor) {
    if (!processor.process(file)) return false;
    if (file.isDirectory() && ((VirtualFileImpl)file).areChildrenCached()) {
      for (final VirtualFile child : file.getChildren()) {
        if (!processFile(child, processor)) return false;
      }
    }
    return true;
  }

  private boolean auxDelete(VirtualFileImpl file) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.delete(file)) return true;
    }

    return false;
  }

  private boolean auxMove(VirtualFile file, VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  @Nullable
  private File auxCopy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      final File copy = handler.copy(file, toDir, copyName);
      if (copy != null) return copy;
    }
    return null;
  }

  private boolean auxRename(VirtualFile file, String newName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  private boolean auxCreateFile(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  private boolean auxCreateDirectory(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  public void removeWatchedRoots(final Collection<WatchRequest> rootsToWatch) {
    WRITE_LOCK.lock();
    try {
      if (myRootsToWatch.removeAll(rootsToWatch)) {
        myCachedNormalizedRequests = null;
        setUpFileWatcher();
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  protected VirtualFile copyFile(final Object requestor, final VirtualFile vFile, final VirtualFile newParent, final String copyName)
    throws IOException {
    final VirtualFileImpl file = (VirtualFileImpl)vFile;
    File physicalCopy = auxCopy(vFile, newParent, copyName);

    if (physicalCopy == null) {
      File physicalFile = file.getPhysicalFile();

      File newPhysicalParent = ((VirtualFileImpl)newParent).getPhysicalFile();
      physicalCopy = new File(newPhysicalParent, copyName);

      try {
        if (physicalFile.isDirectory()) {
          FileUtil.copyDir(physicalFile, physicalCopy);
        }
        else {
          physicalCopy.createNewFile();
          FileUtil.copy(physicalFile, physicalCopy);
        }
      }
      catch (IOException e) {
        FileUtil.delete(physicalCopy);
        throw e;
      }
    }

    final VirtualFileImpl created = new VirtualFileImpl((VirtualFileImpl)newParent, physicalCopy, physicalCopy.isDirectory());
    ((VirtualFileImpl)newParent).addChild(created);
    fireFileCopied(requestor, vFile, created);
    return created;
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    final VirtualFileImpl file = (VirtualFileImpl)vFile;
    String name = vFile.getName();
    VirtualFileImpl oldParent = file.getParent();
    final boolean handled = auxMove(vFile, newParent);

    fireBeforeFileMovement(requestor, vFile, newParent);

    newParent.getChildren(); // Init children.

    File physicalFile = file.getPhysicalFile();
    boolean isDirectory = file.isDirectory();

    if (!handled) {
      File newPhysicalParent = ((VirtualFileImpl)newParent).getPhysicalFile();
      File newPhysicalFile = new File(newPhysicalParent, name);
      if (!physicalFile.renameTo(newPhysicalFile)) {
        throw new IOException(VfsBundle.message("file.move.to.error", physicalFile.getPath(), newPhysicalParent.getPath()));
      }
    }

    oldParent.removeChild(file);

    file.setParent((VirtualFileImpl)newParent);
    ((VirtualFileImpl)newParent).addChild(file);
    //myModificationStamp = LocalTimeCounter.currentTime();
    //myTimeStamp = -1;
    fireFileMoved(requestor, file, oldParent);

    if (handled && isDirectory && physicalFile.exists()) {
      // Some auxHandlers refuse to delete directories actually as per version controls like CVS or SVN.
      // So if the direcotry haven't been deleted actually we must recreate VFS structure for this.
      VirtualFileImpl newMe = new VirtualFileImpl(oldParent, physicalFile, true);
      oldParent.addChild(newMe);
      fireFileCreated(requestor, newMe);
    }
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    final VirtualFileImpl file = (VirtualFileImpl)vFile;

    final boolean handled = auxRename(file, newName);

    String oldName = file.getName();
    fireBeforePropertyChange(requestor, file, VirtualFile.PROP_NAME, oldName, newName);

    if (!handled) {
      File physicalFile = file.getPhysicalFile();
      file.setName(newName);
      File newFile = file.getPhysicalFile();
      if (!physicalFile.renameTo(newFile)) {
        file.setName(physicalFile.getName());
        throw new IOException(VfsBundle.message("file.rename.error", physicalFile.getPath(), newFile.getPath()));
      }
    }
    else {
      file.setName(newName);
    }

    firePropertyChanged(requestor, file, VirtualFile.PROP_NAME, oldName, newName);
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    final VirtualFileImpl file = (VirtualFileImpl)vFile;
    File physicalFile = file.getPhysicalFile();
    VirtualFileImpl parent = file.getParent();
    if (parent == null) {
      throw new IOException(VfsBundle.message("file.delete.root.error", physicalFile.getPath()));
    }

    final String name = file.getName();
    final boolean handled = auxDelete(file);

    if (!handled) {
      delete(physicalFile);
    }

    fireBeforeFileDeletion(requestor, file);

    boolean isDirectory = file.isDirectory();

    parent.removeChild(file);

    fireFileDeleted(requestor, file, name, parent);

    if (handled && isDirectory && physicalFile.exists()) {
      // Some auxHandlers refuse to delete directories actually as per version controls like CVS or SVN.
      // So if the direcotry haven't been deleted actually we must recreate VFS structure for this.
      VirtualFileImpl newMe = new VirtualFileImpl(parent, physicalFile, true);
      parent.addChild(newMe);
      fireFileCreated(requestor, newMe);
    }
  }

  private static void delete(File physicalFile) throws IOException {
    File[] list = physicalFile.listFiles();
    if (list != null) {
      for (File aList : list) {
        delete(aList);
      }
    }
    if (!physicalFile.delete()) {
      throw new IOException(VfsBundle.message("file.delete.error", physicalFile.getPath()));
    }
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    final VirtualFileImpl dir = (VirtualFileImpl)vDir;

    VirtualFile existingFile = dir.findChild(dirName);

    final boolean auxCommand = auxCreateDirectory(vDir, dirName);

    File physicalFile = new File(dir.getPhysicalFile(), dirName);

    if (!auxCommand) {
      if (existingFile != null || physicalFile.exists()) {
        throw new IOException(VfsBundle.message("file.already.exists.error", physicalFile.getPath()));
      }

      if (!physicalFile.mkdir()) {
        throw new IOException(VfsBundle.message("file.create.error", physicalFile.getPath()));
      }
    }
    else {
      if (existingFile != null) return existingFile;
    }

    VirtualFileImpl child = new VirtualFileImpl(dir, physicalFile, true);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    final VirtualFileImpl dir = (VirtualFileImpl)vDir;
    final boolean handled = auxCreateFile(vDir, fileName);

    File physicalFile = new File(dir.getPhysicalFile(), fileName);
    if (!handled) {
      VirtualFile file = dir.findChild(fileName);
      if (file != null || physicalFile.exists()) {
        throw new IOException(VfsBundle.message("file.already.exists.error", physicalFile.getPath()));
      }
      new FileOutputStream(physicalFile).close();
    }

    VirtualFileImpl child = new VirtualFileImpl(dir, physicalFile, false);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }
}
