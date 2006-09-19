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
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.Processor;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.intellij.vfs.local.win32.FileWatcher;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalFileSystemImpl extends LocalFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");
  private VirtualFileManagerEx myManager = null;

  final Object LOCK = new Object();

  private final List<WatchRequest> myRootsToWatch = new ArrayList<WatchRequest>();
  private WatchRequest[] myCachedNormalizedRequests = null;
  private BidirectionalMap<VirtualFile, String> myFSRootsToPaths = null;

  private List<String> myFilePathsToWatchManual = new ArrayList<String>();
  private final Set<String> myDirtyFiles = new HashSet<String>(); // dirty files when FileWatcher is available
  private final Set<String> myDeletedFiles = new HashSet<String>();

  private final ExecutorService mySynchronizeExecutor = Executors.newSingleThreadExecutor();

  private final Map<VirtualFile,Key> myRefreshStatusMap = new WeakHashMap<VirtualFile, Key>(); // VirtualFile --> 'status'
  private static final Key DIRTY_STATUS = Key.create("DIRTY_STATUS");
  private static final Key DELETED_STATUS = Key.create("DELETED_STATUS");

  private List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();
  public Map<String, VirtualFileImpl> myUnaccountedFiles = SystemInfo.isFileSystemCaseSensitive
                                                           ? new THashMap<String, VirtualFileImpl>()
                                                           : new THashMap<String, VirtualFileImpl>(
                                                               new CaseInsensitiveStringHashingStrategy()
                                                             );

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
      } else {
        myRootPath = rootPath.replace(File.separatorChar, '/');
        myFSRootPath = rootPath.replace('/', File.separatorChar);
      }
    }

    @NotNull public String getRootPath() { return myRootPath; }

    @NotNull public String getFileSystemRootPath() { return myFSRootPath; }

    public boolean isToWatchRecursively() { return myToWatchRecursively; }

    public boolean dominates(WatchRequest other) {
      if (myToWatchRecursively) {
        return myRootPath.startsWith(other.getRootPath());
      }

      return myRootPath.equals(other.getRootPath());
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

    synchronized (LOCK) {
      final BidirectionalMap<VirtualFile, String> map = new BidirectionalMap<VirtualFile, String>();

      final File[] files = File.listRoots();
      for (File file : files) {
        String path = getCanonicalPath(file);
        if (path != null) {
          path = path.replace(File.separatorChar, '/');
          final VirtualFileImpl root = new VirtualFileImpl(path);
          map.put(root, path);
        }
      }
      myFSRootsToPaths = map;
    }
  }

  private void refreshFSRoots() {
    if (myFSRootsToPaths == null) {
      initRoots();
    }
    else {
      final File[] files = File.listRoots();

      synchronized (LOCK) {
        Set<String> newRootPaths= new HashSet<String>();
        for (File file : files) {
          String path = getCanonicalPath(file);
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

        for (Iterator<Map.Entry<VirtualFile,String>> iterator = myFSRootsToPaths.entrySet().iterator(); iterator.hasNext();) {
          Map.Entry<VirtualFile,String> entry = iterator.next();
          if (!newRootPaths.contains(entry.getValue())) {
            iterator.remove();
          }
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
    synchronized (LOCK) {
      initRoots();
      return myFSRootsToPaths.containsKey(file);
    }
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(String path) {
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }

    VirtualFile file = _findFileByPath(path, true);
    if (file == null){
      String canonicalPath = getCanonicalPath(new File(path.replace('/', File.separatorChar)));
      if (canonicalPath == null) return null;
      String path1 = canonicalPath.replace(File.separatorChar, '/');
      if (!path.equals(path1)){
        return _findFileByPath(path1, true);
      }
    }
    return file;
  }

  private VirtualFile _findFileByPath(String path, final boolean assertReadAccessAllowed) {
    if (assertReadAccessAllowed) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    return findFileByPath(path, true, false);
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
          } else {
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
                  child = root.findChild(name);
                  if (child == null) return null;
                  //need to fire event here since refresh did not fire, because children are not cached
                  fireFileCreated(null, child);
                  root = child;
                } else {
                  return null;
                }
              } else {
                root = ((VirtualFileImpl)root).findSingleChild(name);
                if (root == null) return null;
              }
            } else {
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
    final VirtualFile file = findFileByPath(path, true, true);
    if (file == null){
      String canonicalPath = getCanonicalPath(new File(path.replace('/', File.separatorChar)));
      if (canonicalPath == null) return null;
      String path1 = canonicalPath.replace(File.separatorChar, '/');
      if (!path.equals(path1)){
        return findFileByPath(path1, true, true);
      }
    }
    return file;
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
    if(!(virtualFile instanceof VirtualFileImpl)) return virtualFile.contentsToByteArray();
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

  public String extractPresentableUrl(String path) {
    return super.extractPresentableUrl(path);
  }

  public void refresh(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    final ModalityState modalityState = VirtualFileManagerImpl.calcModalityStateForRefreshEventsPosting(asynchronous);

    final Runnable endTask = new Runnable() {
      public void run() {
        getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };

    final Runnable runnable = new Runnable() {
      public void run() {
        getManager().beforeRefreshStart(asynchronous, modalityState, null);

        storeRefreshStatusToFiles();

        refreshFSRoots();
        WatchRequest[] requests;
        synchronized (LOCK) {
          requests = normalizeRootsForRefresh();
        }

        for (final WatchRequest request : requests) {
          String runPath = request.getRootPath();
          final VirtualFileImpl rootFile = (VirtualFileImpl)_findFileByPath(runPath, false);
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
                    final VirtualFileImpl parent = rootFile.getParent();
                    if (parent != null) parent.removeChild(rootFile);
                  }
                  fireFileDeleted(null, rootFile, rootFile.getName(), isDirectory, null);
                }
              };
              getManager().addEventToFireByRefresh(action, asynchronous, modalityState);
            }
            else {
              refresh(rootFile, recursively, false, modalityState, asynchronous, true, false);
            }
          } else {
            final String fileSystemPath = request.getFileSystemRootPath();
            checkFileCreated(fileSystemPath, runPath, asynchronous, modalityState);
          }
        }

        final Set<Map.Entry<String, VirtualFileImpl>> entries = new HashSet<Map.Entry<String, VirtualFileImpl>>(myUnaccountedFiles.entrySet());
        for (final Map.Entry<String,VirtualFileImpl> entry : entries) {
          final VirtualFileImpl file = entry.getValue();
          if (file != null && file.isValid()) {
            if (!file.getPhysicalFile().exists()) {
              final Runnable action = new Runnable() {
                public void run() {
                  if (!file.isValid()) return;
                  boolean isDirectory = file.isDirectory();
                  fireBeforeFileDeletion(null, file);
                  synchronized (LOCK) {
                    myUnaccountedFiles.put(entry.getKey(), null);
                  }
                  fireFileDeleted(null, file, file.getName(), isDirectory, null);
                }
              };
              getManager().addEventToFireByRefresh(action, asynchronous, modalityState);

            } else {
              refresh(file, true, false, modalityState, asynchronous, false, false);
            }
          } else {
            final String vfsPath = entry.getKey();
            final String fsPath = vfsPath.replace('/', File.separatorChar);
            checkFileCreated(fsPath, vfsPath, asynchronous, modalityState);
          }
        }

        FileWatcher.resyncedManually();
      }
    };

    if (asynchronous) {
      getSynchronizeExecutor().submit(new Runnable() {
        public void run() {
          LOG.info("Executing request:" + this);
          runnable.run();
          endTask.run();
        }
      });
    }
    else {
      runnable.run();
      endTask.run();
    }
  }

  private void checkFileCreated(final String fileSystemPath,
                                String vfsPath,
                                final boolean asynchronous,
                                final ModalityState modalityState) {
    final boolean physicalExists = new File(fileSystemPath).exists();
    if (physicalExists) {
      int index = vfsPath.lastIndexOf('/');
      while(index >= 0) {
        String parentPath = vfsPath.substring(0, index);
        final VirtualFileImpl vParent = (VirtualFileImpl)_findFileByPath(parentPath, false);
        if (vParent != null) {
          final String path = vfsPath;
          getManager().addEventToFireByRefresh(new Runnable() {
            public void run() {
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
      getSynchronizeExecutor().submit(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
    else {
      runnable.run();
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  void refresh(VirtualFile file,
               boolean recursive,
               boolean storeStatus,
               ModalityState modalityState,
               boolean asynchronous,
               final boolean isRoot,
               final boolean noWatcher) {
    synchronized (LOCK) {
      if (!myFilePathsToWatchManual.isEmpty()) {
        final String filePath = file.getPath();
        for (int i = 0; i < myFilePathsToWatchManual.size(); i++) {
          String pathToWatchManual = myFilePathsToWatchManual.get(i);
          if (FileUtil.startsWith(filePath, pathToWatchManual)) {
            ((VirtualFileImpl)file).refreshInternal(recursive, modalityState, false, asynchronous, noWatcher);
            if ((isRoot || recursive) && ((VirtualFileImpl)file).areChildrenCached()) {
              VirtualFile[] children = file.getChildren();
              for (int j = 0; j < children.length; j++) {
                VirtualFile child = children[j];
                refreshInner(child, recursive, modalityState, asynchronous, false, true);
              }
            }
            return;
          }
        }
      }
    }

    if (storeStatus) {
      storeRefreshStatusToFiles();
    }

    refreshInner(file, recursive, modalityState, asynchronous, isRoot, noWatcher);
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage is produced otherwise in AbstractList.iterator()
  void refreshInner(VirtualFile file, boolean recursive, ModalityState modalityState, boolean asynchronous, final boolean isRoot, final boolean noWatcher) {
    if (noWatcher || !FileWatcher.isAvailable() || !recursive && !asynchronous) { // We're unable to definitely refresh syncronously by means of file watcher.
      ((VirtualFileImpl)file).refreshInternal(recursive, modalityState, false, asynchronous, noWatcher);
      if ((isRoot && !recursive) && ((VirtualFileImpl)file).areChildrenCached()) {  //if recursive, then we have already processed children in refreshInternal
        final VirtualFile[] children = file.getChildren();
        for (int i = 0; i < children.length; i++) {
          VirtualFile child = children[i];
          refreshInner(child, false, modalityState, asynchronous, false, noWatcher);
        }
      }
    }
    else {
      Key status;
      synchronized (myRefreshStatusMap) {
        status = myRefreshStatusMap.remove(file);
      }
      if (status == DELETED_STATUS) {
        if (((VirtualFileImpl)file).getPhysicalFile().exists()) { // file was deleted but later restored - need to rescan the whole subtree
          ((VirtualFileImpl)file).refreshInternal(true, modalityState, false, asynchronous, noWatcher);
        }
      }
      else {
        if (status == DIRTY_STATUS) {
          ((VirtualFileImpl)file).refreshInternal(false, modalityState, false, asynchronous, noWatcher);
        }
        if ((isRoot || recursive) && ((VirtualFileImpl)file).areChildrenCached()) { //here above refresh was not recursive, so in case of recursive we need to trigger it here
          VirtualFile[] children = file.getChildren();
          for (int i = 0; i < children.length; i++) {
            VirtualFile child = children[i];
            if (status == DIRTY_STATUS &&
                !((VirtualFileImpl)child).getPhysicalFile().exists()) {
              continue; // should be already handled above (see SCR6145)
            }
            refreshInner(child, recursive,  modalityState, asynchronous, false, noWatcher);
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

  protected void fireFileDeleted(Object requestor,
                                 VirtualFile file,
                                 String fileName,
                                 boolean isDirectory,
                                 VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, isDirectory, parent);
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

  @Nullable
  private static String getCanonicalPath(File file) {
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
      try {
        //noinspection InfiniteLoopStatement
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
      catch (IOException e) {
        LOG.info("Watcher terminated and attempt to restart has failed. Exiting watching thread.", e);
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
    synchronized (LOCK) {
      final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
      final VirtualFile existingFile = findFileByPath(rootPath, false, false);
      if (existingFile != null) {
        if (!isAlreadyWatched(result)) {
          synchronizeFiles(toWatchRecursively, existingFile);
        }
      }
      myRootsToWatch.add(result); //add in any case
      setUpFileWatcher();
      return result;
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

    synchronized (LOCK) {
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

      setUpFileWatcher();
      if (!filesToSynchronize.isEmpty()) {
        synchronizeFiles(toWatchRecursively, filesToSynchronize.toArray(new VirtualFile[filesToSynchronize.size()]));
      }
    }

    return result;
  }

  private static void synchronizeFiles(final boolean recursively, final VirtualFile... files) {
    for (final VirtualFile file : files) {
      ((VirtualFileImpl)file).refresh(true, recursively, true, null);
    }
  }

  public void removeWatchedRoot(final WatchRequest watchRequest) {
    synchronized (LOCK) {
      if (myRootsToWatch.remove(watchRequest)) setUpFileWatcher();
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
      for (final VirtualFileImpl unaccounted : myUnaccountedFiles.values()) {
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
    synchronized (LOCK) {
      if (myRootsToWatch.removeAll(rootsToWatch)) setUpFileWatcher();
    }
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    final VirtualFileImpl file = (VirtualFileImpl)vFile;
    String name = vFile.getName();
    VirtualFileImpl oldParent = file.getParent();
    final boolean handled = auxMove(vFile, newParent);

    fireBeforeFileMovement(requestor, vFile, newParent);

    newParent.getChildren(); // Init children.

    PhysicalFile physicalFile = file.getPhysicalFile();
    boolean isDirectory = file.isDirectory();

    if (!handled) {
      PhysicalFile newPhysicalParent = ((VirtualFileImpl)newParent).getPhysicalFile();
      PhysicalFile newPhysicalFile = newPhysicalParent.createChild(name);
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
      PhysicalFile physicalFile = file.getPhysicalFile();
      file.setName(newName);
      PhysicalFile newFile = file.getPhysicalFile();
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
    PhysicalFile physicalFile = file.getPhysicalFile();
    VirtualFileImpl parent = file.getParent();
    if (parent == null) {
      throw new IOException(VfsBundle.message("file.delete.root.error", physicalFile.getPath()));
    }

    final String name = file.getName();
    final boolean handled = auxDelete(file);

    fireBeforeFileDeletion(requestor, file);

    boolean isDirectory = file.isDirectory();

    parent.removeChild(file);

    if (!handled) {
      delete(physicalFile);
    }

    fireFileDeleted(requestor, file, name, isDirectory, parent);

    if (handled && isDirectory && physicalFile.exists()) {
      // Some auxHandlers refuse to delete directories actually as per version controls like CVS or SVN.
      // So if the direcotry haven't been deleted actually we must recreate VFS structure for this.
      VirtualFileImpl newMe = new VirtualFileImpl(parent, physicalFile, true);
      parent.addChild(newMe);
      fireFileCreated(requestor, newMe);
    }
  }

  private static void delete(PhysicalFile physicalFile) throws IOException {
    PhysicalFile[] list = physicalFile.listFiles();
    if (list != null) {
      for (PhysicalFile aList : list) {
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

    PhysicalFile physicalFile = dir.getPhysicalFile().createChild(dirName);

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

    PhysicalFile physicalFile = dir.getPhysicalFile().createChild(fileName);
    if (!handled) {
      VirtualFile file = dir.findChild(fileName);
      if (file != null || physicalFile.exists()) {
        throw new IOException(VfsBundle.message("file.already.exists.error", physicalFile.getPath()));
      }
      physicalFile.createOutputStream().close();
    }

    VirtualFileImpl child = new VirtualFileImpl(dir, physicalFile, false);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }
}
