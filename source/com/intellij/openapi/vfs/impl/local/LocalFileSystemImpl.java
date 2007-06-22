package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.fs.IFile;
import com.intellij.vfs.local.win32.FileWatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
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

  private final Set<String> myDirtyFiles = new HashSet<String>(); // dirty files when FileWatcher is available
  private final Set<String> myDeletedFiles = new HashSet<String>();

  private List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();
  private List<String> myManualWatchRoots = new ArrayList<String>();

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

    final VirtualFile[] roots = ManagingFS.getInstance().getRoots(this);
    for (VirtualFile root : roots) {
      if (root instanceof VirtualDirectoryImpl) {
        final VirtualDirectoryImpl directory = (VirtualDirectoryImpl)root;
        directory.cleanupCachedChildren();
      }
    }

    myRootsToWatch.clear();
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

  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull String path) {
    /*
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }
    */

    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPath(canonicalPath);
  }

  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPathIfCached(canonicalPath);
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(String path) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.refreshAndFindFileByPath(canonicalPath);
  }

  public String normalize(final String path) {
    return getVfsCanonicalPath(path);
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
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    manager.fireBeforeRefreshStart(false);

    List<VirtualFile> filesToRefresh = new ArrayList<VirtualFile>();

    for (File file : files) {
      final VirtualFile virtualFile = refreshAndFindFileByIoFile(file);
      if (virtualFile != null) {
        filesToRefresh.add(virtualFile);
      }
    }

    RefreshQueue.getInstance().refresh(false, false, null, filesToRefresh.toArray(new VirtualFile[filesToRefresh.size()]));
    
    manager.fireAfterRefreshFinish(false);
  }

  public void refreshFiles(Iterable<VirtualFile> files) {
    refreshFiles(files, false);
  }

  private static void refreshFiles(final Iterable<VirtualFile> files, final boolean recursive) {
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      list.add(file);
    }

    RefreshQueue.getInstance().refresh(false, recursive, null, list.toArray(new VirtualFile[list.size()]));
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    return virtualFile.contentsToByteArray();
  }

  public long physicalLength(final VirtualFile virtualFile) throws IOException {
    return virtualFile.getLength();
  }

  public String extractPresentableUrl(String path) {
    return super.extractPresentableUrl(path);
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

  public void storeRefreshStatusToFiles() {
    if (FileWatcher.isAvailable()) {
      final String[] dirtyFiles;
      synchronized (myDirtyFiles) {
        dirtyFiles = myDirtyFiles.toArray(new String[myDirtyFiles.size()]);
        myDirtyFiles.clear();
      }
      for (String dirtyFile : dirtyFiles) {
        String path = dirtyFile.replace(File.separatorChar, '/');
        VirtualFile file = findFileByPathIfCached(path);
        if (file instanceof NewVirtualFile) {
          ((NewVirtualFile)file).markDirty();
        }
      }

      final String[] deletedFiles;
      synchronized (myDeletedFiles) {
        deletedFiles = myDeletedFiles.toArray(new String[myDeletedFiles.size()]);
        myDeletedFiles.clear();
      }
      for (String deletedFile : deletedFiles) {
        String path = deletedFile.replace(File.separatorChar, '/');
        VirtualFile file = findFileByPathIfCached(path);
        if (file instanceof NewVirtualFile) {
          ((NewVirtualFile)file).markDirty();
        }
      }
    }
  }

  public void markSuspicousFilesDirty(List<VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (FileWatcher.isAvailable()) {
      for (String root : myManualWatchRoots) {
        final VirtualFile suspicousRoot = findFileByPathIfCached(root);
        if (suspicousRoot != null) {
          ((NewVirtualFile)suspicousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (VirtualFile file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  @Nullable
  private static String getVfsCanonicalPath(String path) {
    if (path.length() == 0) {
      try {
        return new File("").getCanonicalPath();
      }
      catch (IOException e) {
        return path;
      }
    }

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

            myManualWatchRoots = new ArrayList<String>();
            for (int i = 0; i < watchManual.size(); i++) {
              myManualWatchRoots.add(watchManual.elementAt(i));
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
        if (ApplicationManager.getApplication().isDisposed()) break;
        
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
    if (rootPath.length() == 0) return null;

    WRITE_LOCK.lock();
    try {
      final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
      final VirtualFile existingFile = findFileByPathIfCached(rootPath);
      if (existingFile != null) {
        if (!isAlreadyWatched(result)) {
          existingFile.refresh(true, toWatchRecursively);
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
        if (rootPath.length() > 0) {
          final WatchRequestImpl request = new WatchRequestImpl(rootPath, toWatchRecursively);
          final VirtualFile existingFile = findFileByPathIfCached(rootPath);
          if (existingFile != null) {
            if (!isAlreadyWatched(request)) {
              filesToSynchronize.add(existingFile);
            }
          }
          result.add(request);
          myRootsToWatch.add(request); //add in any case, safe to add inplace without copying myRootsToWatch before the loop
        }
      }
      myCachedNormalizedRequests = null;
      setUpFileWatcher();
    }
    finally {
      WRITE_LOCK.unlock();
    }
    if (!filesToSynchronize.isEmpty()) {
      refreshFiles(filesToSynchronize, toWatchRecursively);
    }

    return result;
  }

  public void removeWatchedRoot(@NotNull final WatchRequest watchRequest) {
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

    return processFile((NewVirtualFile)file, processor);
  }

  private static boolean processFile(NewVirtualFile file, Processor<VirtualFile> processor) {
    if (!processor.process(file)) return false;
    if (file.isDirectory()) {
      for (final VirtualFile child : file.getCachedChildren()) {
        if (!processFile((NewVirtualFile)child, processor)) return false;
      }
    }
    return true;
  }

  private boolean auxDelete(VirtualFile file) throws IOException {
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

  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch) {
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

  public boolean isReadOnly() {
    return false;
  }

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
  private static File convertToIOFile(VirtualFile file) {
    String path = file.getPath();
    if (path.endsWith(":") && path.length() == 2 && (SystemInfo.isWindows || SystemInfo.isOS2)) {
      path += "/"; // Make 'c:' resolve to a root directory for drive c:, not the current directory on that drive
    }

    return new File(path);
  }

  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    final File ioDir = new File(convertToIOFile(parent), dir);
    final boolean succ = auxCreateDirectory(parent, dir) || ioDir.mkdirs();
    if (!succ) {
      throw new IOException("Failed to create directory: " + ioDir.getPath());
    }

    return new FakeVirtualFile(dir, parent);
  }

  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    final File ioFile = new File(convertToIOFile(parent), file);
    final boolean succ = auxCreateFile(parent, file) || ioFile.createNewFile();
    if (!succ) {
      throw new IOException("Failed to create child file at " + ioFile.getPath());
    }

    return new FakeVirtualFile(file, parent);
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    if (!auxDelete(file)) {
      delete(convertToIOFile(file));
    }
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) return true;
    return convertToIOFile(fileOrDirectory).exists();
  }

  public long getCRC(final VirtualFile file) {
    return -1;
  }

  public long getLength(final VirtualFile file) {
    return convertToIOFile(file).length();
  }

  public boolean isCaseSensitive() {
    return SystemInfo.isFileSystemCaseSensitive;
  }

  public InputStream getInputStream(final VirtualFile file) throws FileNotFoundException {
    return new BufferedInputStream(new FileInputStream(convertToIOFile(file)));
  }

  public long getTimeStamp(final VirtualFile file) {
    return convertToIOFile(file).lastModified();
  }

  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws FileNotFoundException {
    final File ioFile = convertToIOFile(file);
    return new BufferedOutputStream(new FileOutputStream(ioFile)) {
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0) {
          ioFile.setLastModified(timeStamp);
        }
      }
    };
  }

  public boolean isDirectory(final VirtualFile file) {
    return convertToIOFile(file).isDirectory();
  }

  public boolean isWritable(final VirtualFile file) {
    return convertToIOFile(file).canWrite();
  }

  public String[] list(final VirtualFile file) {
    if (file.getParent() == null) {
      final File[] roots = File.listRoots();
      if (roots.length == 1 && roots[0].getName().length() == 0) {
        return roots[0].list();
      }
    }

    final String[] names = convertToIOFile(file).list();
    return names != null ? names : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException {
    if (!auxMove(file, newParent)) {
      final File ioFrom = convertToIOFile(file);
      final File ioParent = convertToIOFile(newParent);
      ioFrom.renameTo(new File(ioParent, file.getName()));
    }
  }

  public void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException {
    if (!file.exists()) {
      throw new IOException("File to move does not exist: " + file.getPath());
    }

    final VirtualFile parent = file.getParent();
    assert parent != null;

    if (!auxRename(file, newName)) {
      if (!convertToIOFile(file).renameTo(new File(convertToIOFile(parent), newName))) {
        throw new IOException("Destination already exists: " + parent.getPath() + "/" + newName);
      }
    }
  }

  public VirtualFile copyFile(final Object requestor, final VirtualFile vFile, final VirtualFile newParent, final String copyName)
    throws IOException {
    File physicalCopy = auxCopy(vFile, newParent, copyName);

    if (physicalCopy == null) {
      File physicalFile = convertToIOFile(vFile);

      File newPhysicalParent = convertToIOFile(newParent);
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

    return new FakeVirtualFile(copyName, newParent);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) {
    convertToIOFile(file).setLastModified(modstamp);
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    FileUtil.setReadOnlyAttribute(file.getPath(), !writableFlag);
  }

  @NonNls
  public String toString() {
    return "LocalFileSystem";
  }

  public String extractRootPath(final String path) {
    if (path.length() == 0) {
      try {
        return extractRootPath(new File("").getCanonicalPath());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    if (SystemInfo.isWindows) {
      if (path.length() >= 2 && path.charAt(1) == ':') {
        // Drive letter
        return path.substring(0, 2).toUpperCase(Locale.US);
      }

      if (path.startsWith("//") || path.startsWith("\\\\")) {
        // UNC. Must skip exactly two path elements like [\\ServerName\ShareName]\pathOnShare\file.txt
        // Root path is in square brackets here.

        int slashCount = 0;
        int idx;
        for (idx = 2; idx < path.length() && slashCount < 2; idx++) {
          final char c = path.charAt(idx);
          if (c == '\\' || c == '/') {
            slashCount++;
            idx--;
          }
        }

        return path.substring(0, idx);
      }
    }

    return "/";
  }

  public int getRank() {
    return 1;
  }


  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = new Runnable() {
      public void run() {
        for (VirtualFile root : ManagingFS.getInstance().getRoots(LocalFileSystemImpl.this)) {
          ((NewVirtualFile)root).markDirtyRecursively();
        }

        refresh(asynchronous);
      }
    };

    if (asynchronous && FileWatcher.isAvailable()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, ManagingFS.getInstance().getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  public boolean markNewFilesAsDirty() {
    return true;
  }
}
