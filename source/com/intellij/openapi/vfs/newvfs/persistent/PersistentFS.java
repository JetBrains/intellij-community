/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DupOutputStream;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

public class PersistentFS extends ManagingFS implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.PersistentFS");
  private final static FileAttribute FILE_CONTENT = new FileAttribute("PersistentFS.File.Contents", 1);

  private static final int CHILDREN_CACHED_FLAG = 0x01;
  private static final int IS_DIRECTORY_FLAG = 0x02;
  private static final int IS_READ_ONLY = 0x04;
  private static final int MUST_RELOAD_CONTENT = 0x08;

  private static final long FILE_LENGTH_TO_CACHE_THRESHOULD = 20 * 1024 * 1024; // 20 megabytes

  private final FSRecords myRecords;
  private final MessageBus myEventsBus;

  private Map<String, NewVirtualFile> myRoots = new HashMap<String, NewVirtualFile>();

  public PersistentFS(MessageBus bus) {
    myEventsBus = bus;
    myRecords = new FSRecords();
  }

  public void disposeComponent() {
    myRecords.dispose();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "app.component.PersistentFS";
  }

  public void initComponent() {
    try {
      myRecords.connect();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static NewVirtualFileSystem getDelegate(VirtualFile file) {
    return (NewVirtualFileSystem)file.getFileSystem();
  }

  public boolean areChildrenLoaded(final VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  public String[] list(final VirtualFile file) {
    int id = getFileId(file);
    if (areChildrenLoaded(id)) {
      return listPersisted(file);
    }
    else {
      return persistAllChildren(file);
    }
  }

  public String[] listPersisted(final VirtualFile file) {
    return listPersisted(getFileId(file));
  }

  private String[] listPersisted(final int id) {
    final int[] childrenIds = myRecords.list(id);
    String[] names = new String[childrenIds.length];
    for (int i = 0; i < childrenIds.length; i++) {
      names[i] = myRecords.getName(childrenIds[i]);
    }
    return names;
  }

  private String[] persistAllChildren(final VirtualFile file) {
    int id = getFileId(file);
    String[] currentNames = listPersisted(file);
    int[] currentIds = myRecords.list(id);

    final NewVirtualFileSystem delegate = getDelegate(file);
    String[] names = delegate.list(file);
    final int[] childrenIds = new int[names.length];

    for (int i = 0; i < names.length; i++) {
      final String name = names[i];
      int idx = ArrayUtil.indexOf(currentNames, name);
      if (idx >= 0) {
        childrenIds[i] = currentIds[idx];
      }
      else {
        int childId = myRecords.createRecord();
        copyRecordFromDelegateFS(childId, id, new FakeVirtualFile(name, file), delegate);
        childrenIds[i] = childId;
      }
    }

    myRecords.updateList(id, childrenIds);
    int flags = myRecords.getFlags(id);
    myRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG);

    return names;
  }

  public int[] listIds(VirtualFile parent) {
    final int parentId = getFileId(parent);

    if (!areChildrenLoaded(parentId)) {
      list(parent);
    }

    return myRecords.list(parentId);
  }


  public boolean areChildrenLoaded(final int parentId) {
    final int mask = CHILDREN_CACHED_FLAG;
    return (myRecords.getFlags(parentId) & mask) != 0;
  }

  @Nullable
  public DataInputStream readAttribute(final VirtualFile file, final FileAttribute att) {
    return myRecords.readAttribute(getFileId(file), att.getId());
  }

  public DataOutputStream writeAttribute(final VirtualFile file, final FileAttribute att) {
    return myRecords.writeAttribute(getFileId(file), att.getId());
  }

  public int getModificationCount(final VirtualFile file) {
    final int id = getFileId(file);
    return myRecords.getModCount(id);
  }

  public int getFilesystemModificationCount() {
    return myRecords.getModCount();
  }

  private void copyRecordFromDelegateFS(final int id, final int parentId, final VirtualFile file, NewVirtualFileSystem delegate) {
    if (id == parentId) {
      LOG.error("Cyclic parent-child relations for file: " + file);
      return;
    }

    String name = file.getName();

    if (name.length() > 0 && namesEqual(delegate, name, myRecords.getName(id))) return; // TODO: Handle root attributes change.

    if (name.length() == 0) {            // TODO: hack
      if (areChildrenLoaded(id)) return;
    }

    myRecords.setParent(id, parentId);
    myRecords.setName(id, name);

    myRecords.setTimestamp(id, delegate.getTimeStamp(file));
    myRecords.setFlags(id, (delegate.isDirectory(file) ? IS_DIRECTORY_FLAG : 0) | (!delegate.isWritable(file) ? IS_READ_ONLY : 0));

    myRecords.setLength(id, -1L);

    // TODO!!!: More attributes?
  }

  public boolean isDirectory(final VirtualFile file) {
    final int id = getFileId(file);
    return isDirectory(id);
  }

  public boolean isDirectory(final int id) {
    return (myRecords.getFlags(id) & IS_DIRECTORY_FLAG) != 0;
  }

  private static boolean namesEqual(VirtualFileSystem fs, String n1, String n2) {
    return ((NewVirtualFileSystem)fs).isCaseSensitive() ? n1.equals(n2) : n1.equalsIgnoreCase(n2);
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return ((NewVirtualFile)fileOrDirectory).getId() > 0;
  }

  public long getTimeStamp(final VirtualFile file) {
    final int id = getFileId(file);
    return myRecords.getTimestamp(id);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    final int id = getFileId(file);

    myRecords.setTimestamp(id, modstamp);
    getDelegate(file).setTimeStamp(file, modstamp);
  }

  private static int getFileId(final VirtualFile file) {
    final int id = ((NewVirtualFile)file).getId();
    if (id <= 0) {
      throw new InvalidVirtualFileAccessException(file);
    }
    return id;
  }

  public boolean isWritable(final VirtualFile file) {
    final int id = getFileId(file);

    return (myRecords.getFlags(id) & IS_READ_ONLY) == 0;
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    getDelegate(file).setWritable(file, writableFlag);
    processEvent(new VFilePropertyChangeEvent(this, file, VirtualFile.PROP_WRITABLE, isWritable(file), writableFlag, false));
  }

  public int getId(final VirtualFile parent, final String childName) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    final int parentId = getFileId(parent);

    final int[] children = myRecords.list(parentId);
    for (final int childId : children) {
      if (namesEqual(delegate, childName, myRecords.getName(childId))) return childId;
    }

    VirtualFile fake = new FakeVirtualFile(childName, parent);
    if (delegate.exists(fake)) {
      int child = myRecords.createRecord();
      copyRecordFromDelegateFS(child, parentId, fake, delegate);
      myRecords.updateList(parentId, ArrayUtil.append(children, child));
      return child;
    }

    return 0;
  }

  public long getLength(final VirtualFile file) {
    final int id = getFileId(file);

    long len = myRecords.getLength(id);
    if (len == -1) {
      len = (int)getDelegate(file).getLength(file);
      myRecords.setLength(id, len);
    }

    return len;
  }

  public VirtualFile copyFile(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String copyName) throws IOException {
    getDelegate(file).copyFile(requestor, file, newParent, copyName);
    processEvent(new VFileCopyEvent(requestor, file, newParent, copyName));

    final VirtualFile child = newParent.findChild(copyName);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    getDelegate(parent).createChildDirectory(requestor, parent, dir);
    processEvent(new VFileCreateEvent(requestor, parent, dir, true, false));

    final VirtualFile child = parent.findChild(dir);
    if (child == null) {
      throw new IOException("Cannot create child directory '" + dir + "' at " + parent.getPath());
    }
    return child;
  }

  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    getDelegate(parent).createChildFile(requestor, parent, file);
    processEvent(new VFileCreateEvent(requestor, parent, file, false, false));

    final VirtualFile child = parent.findChild(file);
    if (child == null) {
      throw new IOException("Cannot create child file '" + file + "' at " + parent.getPath());
    }
    return child;
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    final NewVirtualFileSystem delegate = getDelegate(file);
    delegate.deleteFile(requestor, file);

    if (!delegate.exists(file)) {
      processEvent(new VFileDeleteEvent(requestor, file, false));
    }
  }

  public void renameFile(final Object requestor, final VirtualFile file, final String newName) throws IOException {
    getDelegate(file).renameFile(requestor, file, newName);
    processEvent(new VFilePropertyChangeEvent(requestor, file, VirtualFile.PROP_NAME, file.getName(), newName, false));
  }

  public InputStream getInputStream(final VirtualFile file) throws IOException {
    InputStream contentStream = FILE_CONTENT.readAttribute(file);
    if (contentStream == null || mustReloadContent(file)) {
      if (contentStream != null) contentStream.close();
      setFlag(file, MUST_RELOAD_CONTENT, false);

      final NewVirtualFileSystem delegate = getDelegate(file);
      final long len = delegate.getLength(file);
      final InputStream nativeStream = delegate.getInputStream(file);

      if (len > FILE_LENGTH_TO_CACHE_THRESHOULD) return nativeStream;

      final ByteArrayOutputStream cache = new ByteArrayOutputStream((int)len);
      return new ReplicatorInputStream(nativeStream, cache) {
        public void close() throws IOException {
          super.close();

          if (getBytesRead() == len) {
            DataOutputStream sink = FILE_CONTENT.writeAttribute(file);
            try {
              FileUtil.copy(new ByteArrayInputStream(cache.toByteArray()), sink);
            }
            finally {
              sink.close();
            }

            myRecords.setLength(getFileId(file), len);
          }
          else {
            setFlag(file, MUST_RELOAD_CONTENT, true);
          }
        }
      };
    }
    else {
      return contentStream;
    }
  }

  private boolean mustReloadContent(final VirtualFile file) {
    return checkFlag(file, MUST_RELOAD_CONTENT) || myRecords.getLength(getFileId(file)) == -1L;
  }

  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    final VFileContentChangeEvent event = new VFileContentChangeEvent(requestor, file, file.getModificationStamp(), modStamp, false);

    final List<VFileContentChangeEvent> events = Collections.singletonList(event);

    final BulkFileListener publisher = myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES);
    publisher.before(events);

    final ByteArrayOutputStream stream = new ByteArrayOutputStream() {
      public void close() throws IOException {
        super.close();

        final OutputStream delegate = getDelegate(file).getOutputStream(file, requestor, modStamp, timeStamp);

        //noinspection IOResourceOpenedButNotSafelyClosed
        final DupOutputStream sink = new DupOutputStream(new BufferedOutputStream(FILE_CONTENT.writeAttribute(file)), delegate) {
          public void close() throws IOException {
            try {
              super.close();
            }
            finally {
              executeTouch(file, false, event.getModificationStamp());
              publisher.after(events);
            }
          }
        };

        try {
          sink.write(buf, 0, count);
        }
        finally {
          sink.close();
        }
      }
    };

    final byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }
    
    return stream;
  }

  public void moveFile(final Object requestor, final VirtualFile file, final VirtualFile newParent) throws IOException {
    getDelegate(file).moveFile(requestor, file, newParent);
    processEvent(new VFileMoveEvent(requestor, file, newParent));
  }

  private void processEvent(VFileEvent event) {
    processEvents(Collections.singletonList(event));
  }

  private static List<? extends VFileEvent> validateEvents(List<? extends VFileEvent> events) {
    List<VFileEvent> filtered = new ArrayList<VFileEvent>(events.size());
    for (VFileEvent event : events) {
      if (event.isValid()) {
        filtered.add(event);
      }
    }

    return filtered;
  }

  public void processEvents(List<? extends VFileEvent> events) {
    events = validateEvents(events);

    myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(events);
    for (VFileEvent event : events) {
      applyEvent(event);
    }
    myEventsBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(events);
  }

  private final Object LOCK = new Object();

  public NewVirtualFile findRoot(final String basePath, final NewVirtualFileSystem fs) { // TODO: read/write locks instead of sycnrhonized
    synchronized (LOCK) {
      final String rootUrl = fs.getProtocol() + "://" + basePath;
      NewVirtualFile root = myRoots.get(rootUrl);
      if (root == null) {
        try {
          final int rootId = myRecords.findRootRecord(rootUrl);
          root = new VirtualDirectoryImpl(basePath, null, fs, rootId);
          if (!fs.exists(root)) return null;

          copyRecordFromDelegateFS(rootId, 0, root, fs);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        myRoots.put(rootUrl, root);
      }

      return root;
    }
  }

  public void refresh(final boolean asynchronous) {
    final NewVirtualFile[] roots;
    synchronized (LOCK) {
      roots = myRoots.values().toArray(new NewVirtualFile[0]);
    }

    RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
  }

  public VirtualFile[] getRoots() {
    List<NewVirtualFile> roots = new ArrayList<NewVirtualFile>(myRoots.values());
    Collections.sort(roots, new Comparator<NewVirtualFile>() {
      public int compare(final NewVirtualFile f1, final NewVirtualFile f2) {
        final NewVirtualFileSystem fs1 = f1.getFileSystem();
        final NewVirtualFileSystem fs2 = f2.getFileSystem();

        return fs1.getRank() - fs2.getRank();
      }
    });

    return roots.toArray(new VirtualFile[roots.size()]);
  }

  public VirtualFile[] getRoots(final NewVirtualFileSystem fs) {
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    synchronized (LOCK) {
      for (NewVirtualFile root : myRoots.values()) {
        if (root.getFileSystem() == fs) {
          roots.add(root);
        }
      }
    }

    return roots.toArray(new VirtualFile[roots.size()]);
  }

  private void applyEvent(final VFileEvent event) {
    /*System.out.println("Apply: " + event);*/

    if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent createEvent = (VFileCreateEvent)event;
      executeCreateChild(createEvent.getParent(), createEvent.getChildName());
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
      executeDelete(deleteEvent.getFile());
    }
    else if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent contentUpdateEvent = (VFileContentChangeEvent)event;
      executeTouch(contentUpdateEvent.getFile(), contentUpdateEvent.isFromRefresh(), contentUpdateEvent.getModificationStamp());
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
      executeCopy(copyEvent.getFile(), copyEvent.getNewParent(), copyEvent.getNewChildName());
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
      executeMove(moveEvent.getFile(), moveEvent.getNewParent());
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
      if (VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())) {
        executeRename(propertyChangeEvent.getFile(), (String)propertyChangeEvent.getNewValue());
      }
      else if (VirtualFile.PROP_WRITABLE.equals(propertyChangeEvent.getPropertyName())) {
        executeSetWritable(propertyChangeEvent.getFile(), ((Boolean)propertyChangeEvent.getNewValue()).booleanValue());
      }
    }
  }

  @NonNls
  public String toString() {
    return "PersistentFS";
  }

  private void executeCreateChild(final VirtualFile parent, final String name) {
    final NewVirtualFileSystem delegate = getDelegate(parent);
    VirtualFile fakeFile = new FakeVirtualFile(name, parent);
    if (delegate.exists(fakeFile)) {
      final int parentId = getFileId(parent);
      int childId = myRecords.createRecord();
      copyRecordFromDelegateFS(childId, parentId, fakeFile, delegate);

      appendIdToParentList(parentId, childId);
      final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)parent;
      dir.addChild(dir.createChild(name, childId));
    }
  }

  private void appendIdToParentList(final int parentId, final int childId) {
    int[] childrenlist = myRecords.list(parentId);
    childrenlist = ArrayUtil.append(childrenlist, childId);
    myRecords.updateList(parentId, childrenlist);
  }

  private void executeDelete(final VirtualFile file) {
    if (!file.exists()) {
      LOG.error("Deleting a file, which does not exist: " + file.getPath());
    }
    else {
      final int id = getFileId(file);

      final VirtualFile parent = file.getParent();
      final int parentId = parent != null ? getFileId(parent) : 0;

      myRecords.deleteRecordRecursively(id);

      if (parentId != 0) {
        removeIdFromParentList(parentId, id);
        ((VirtualDirectoryImpl)file.getParent()).removeChild(file);
      }
      else {
        synchronized (LOCK) {
          myRoots.remove(file.getUrl());
          try {
            myRecords.deleteRootRecord(id);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }

      invalidateSubtree(file);
    }
  }

  private static void invalidateSubtree(final VirtualFile file) {
    final VirtualFileSystemEntry impl = (VirtualFileSystemEntry)file;
    impl.invalidate();
    for (VirtualFile child : impl.getCachedChildren()) {
      invalidateSubtree(child);
    }
  }

  private void removeIdFromParentList(final int parentId, final int id) {
    int[] childList = myRecords.list(parentId);
    childList = ArrayUtil.remove(childList, ArrayUtil.indexOf(childList, id));
    myRecords.updateList(parentId, childList);
  }

  private void executeRename(final VirtualFile file, final String newName) {
    ((VirtualFileSystemEntry)file).setName(newName);
    final int id = getFileId(file);
    myRecords.setName(id, newName);
  }

  private void executeSetWritable(final VirtualFile file, final boolean writableFlag) {
    setFlag(file, IS_READ_ONLY, !writableFlag);
  }

  private void setFlag(VirtualFile file, int mask, boolean value) {
    setFlag(getFileId(file), mask, value);
  }

  private void setFlag(final int id, final int mask, final boolean value) {
    int oldFlags = myRecords.getFlags(id);
    int flags = value ? (oldFlags | mask) : (oldFlags & (~mask));

    if (oldFlags != flags) {
      myRecords.setFlags(id, flags);
    }
  }

  private boolean checkFlag(VirtualFile file, int mask) {
    return (myRecords.getFlags(getFileId(file)) & mask) != 0;
  }

  private void executeTouch(final VirtualFile file, boolean reloadContentFromDelegate, long newModificationStamp) {
    if (reloadContentFromDelegate) {
      setFlag(file, MUST_RELOAD_CONTENT, true);
    }

    final NewVirtualFileSystem delegate = getDelegate(file);
    myRecords.setLength(getFileId(file), delegate.getLength(file));
    myRecords.setTimestamp(getFileId(file), delegate.getTimeStamp(file));

    ((NewVirtualFile)file).setModificationStamp(newModificationStamp);
  }

  private void executeCopy(final VirtualFile from, final VirtualFile newParent, final String copyName) {
    executeCreateChild(newParent, copyName);
  }

  private void executeMove(final VirtualFile what, final VirtualFile newParent) {
    final int whatId = getFileId(what);
    final int newParentId = getFileId(newParent);
    final int oldParentId = getFileId(what.getParent());

    removeIdFromParentList(oldParentId, whatId);
    appendIdToParentList(newParentId, whatId);

    ((VirtualFileSystemEntry)what).setParent(newParent);
  }

  public String getName(final int id) {
    return myRecords.getName(id);
  }

  public void cleanPersistedContents() {
    try {
      final int[] roots = myRecords.listRoots();
      for (int root : roots) {
        cleanPersistedContentsRecursively(root);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void cleanPersistedContentsRecursively(int id) {
    if (isDirectory(id)) {
      for (int child : myRecords.list(id)) {
        cleanPersistedContentsRecursively(child);
      }
    }
    else {
      setFlag(id, MUST_RELOAD_CONTENT, true);
    }
  }
}