/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

public class VFileImpl extends NewVirtualFile {
  private static final PersistentFS ourPersistence = (PersistentFS)ManagingFS.getInstance();

  private volatile NewVirtualFileSystem myFS;
  private volatile String myName;
  private volatile VFileImpl myParent;
  private volatile Object myChildren; // Either HashMap<String, VFile> or VFile[]
  private volatile boolean myIsDirectory = false;
  private volatile boolean myIsDirectoryCached = false;
  private volatile boolean myDirtyFlag = false;
  private volatile int myId;

  public VFileImpl(final String name, final VirtualFile parent, final NewVirtualFileSystem fs, int id) {
    myFS = fs;
    myName = name;
    myParent = (VFileImpl)parent;
    myId = id;
  }

  @NotNull
  public String getName() {
    // TODO: HACK!!! Get to simpler solution.
    if (myParent == null && getFileSystem() instanceof JarFileSystem) {
      String jarName = myName.substring(0, myName.length() - JarFileSystem.JAR_SEPARATOR.length());
      return jarName.substring(jarName.lastIndexOf('/') + 1);
    }

    return myName;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  private void appendPathOnFileSystem(StringBuilder builder, boolean includeFSBaseUrl) {
    if (myParent != null) {
      myParent.appendPathOnFileSystem(builder, includeFSBaseUrl);
    }
    else {
      if (includeFSBaseUrl) {
        builder.append(getFileSystem().getProtocol()).append("://");
      }
    }

    if (myName.length() > 0) {
      final int len = builder.length();
      if (len > 0 && builder.charAt(len - 1) != '/') {
        builder.append('/');
      }
      builder.append(myName);
    }
  }

  public boolean isDirty() {
    return myDirtyFlag;
  }

  public void markClean() {
    myDirtyFlag = false;
  }

  public void markDirty() {
    if (!myDirtyFlag) {
      myDirtyFlag = true;
      if (myParent != null) myParent.markDirty();
    }
  }

  public void markDirtyReqursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((VFileImpl)file).markDirtyReqursively();
    }
  }

  @NotNull
  public String getUrl() {
    StringBuilder builder = new StringBuilder();
    appendPathOnFileSystem(builder, true);
    return builder.toString();
  }

  @NotNull
  public String getPath() {
    StringBuilder builder = new StringBuilder();
    appendPathOnFileSystem(builder, false);
    return builder.toString();
  }

  public void delete(final Object requestor) throws IOException {
    ourPersistence.deleteFile(requestor, this);
  }

  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!VfsUtil.isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    ourPersistence.renameFile(requestor, this, newName);
  }

  @NotNull
  public VirtualFile createChildData(final Object requestor, @NotNull final String name) throws IOException {
    return ourPersistence.createChildFile(requestor, this, name);
  }

  private Map<String, VirtualFile> createMap() {
    return getFileSystem().isCaseSensitive()
           ? new THashMap<String, VirtualFile>()
           : new THashMap<String, VirtualFile>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  private boolean namesEqual(final String name1, final String name2) {
    return getFileSystem().isCaseSensitive() ? name1.equals(name2) : name1.equalsIgnoreCase(name2);
  }

  public boolean isWritable() {
    return ourPersistence.isWritable(this);
  }

  public void setWritable(boolean writable) throws IOException {
    ourPersistence.setWritable(this, writable);
  }

  public long getTimeStamp() {
    return ourPersistence.getTimeStamp(this);
  }

  public void setTimeStamp(final long time) throws IOException {
    ourPersistence.setTimeStamp(this, time);
  }

  public long getLength() {
    return ourPersistence.getLength(this);
  }

  public VirtualFile copy(final Object requestor, final VirtualFile newParent, final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return ourPersistence.copyFile(requestor, this, newParent, copyName);
  }

  public void move(final Object requestor, final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    ourPersistence.moveFile(requestor, this, newParent);
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, final String name) throws IOException {
    return ourPersistence.createChildDirectory(requestor, this, name);
  }

  public boolean exists() {
    return ourPersistence.exists(this);
  }

  @Nullable
  public NewVirtualFile findChild(@NotNull final String name) {
    return findChild(name, false);
  }

  @Nullable
  private NewVirtualFile findChild(final String name, final boolean createIfNotFound) {
    final NewVirtualFile result = doFindChild(name, createIfNotFound);
    if (result == null && myChildren instanceof Map) {
      ensureAsMap().put(name, NullVirtualFile.INSTANCE);
    }
    return result;
  }

  @Nullable
  private NewVirtualFile doFindChild(final String name, final boolean createIfNotFound) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      for (VirtualFile file : a) {
        if (namesEqual(name, file.getName())) return (NewVirtualFile)file;
      }

      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    final Map<String, VirtualFile> map = ensureAsMap();
    final VirtualFile file = map.get(name);
    if (file == NullVirtualFile.INSTANCE) {
      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    if (file != null) return (NewVirtualFile)file;

    int id = ourPersistence.getId(this, name);
    if (id > 0) {
      NewVirtualFile child = new VFileImpl(name, this, getFileSystem(), id);
      map.put(child.getName(), child);
      return child;
    }

    return null;
  }

  @Nullable
  private NewVirtualFile createAndFindChildWithEventFire(final String name) {
    VirtualFile fake = new VFileImpl(name, this, myFS, 0);
    final NewVirtualFileSystem delegate = getFileSystem();
    if (delegate.exists(fake)) {
      VFileCreateEvent event = new VFileCreateEvent(null, this, fake.getName(), delegate.isDirectory(fake), true);
      RefreshQueue.getInstance().processSingleEvent(null, event);
      return findChild(fake.getName());
    }
    else {
      return null;
    }
  }

  @Nullable
  public NewVirtualFile refreshAndFindChild(final String name) {
    return findChild(name, true);
  }

  @NotNull
  public VirtualFile[] getChildren() {
    if (myChildren instanceof VirtualFile[]) {
      return (VirtualFile[])myChildren;
    }

    final int[] childrenIds = ourPersistence.listIds(this);
    VirtualFile[] children = new VirtualFile[childrenIds.length];
    final Map<String, VirtualFile> map = asMap();
    for (int i = 0; i < children.length; i++) {
      final int childId = childrenIds[i];
      final String name = ourPersistence.getName(childId);
      VirtualFile child = map != null ? map.get(name) : null;

      children[i] = child != null && child != NullVirtualFile.INSTANCE ? child : new VFileImpl(name, this, getFileSystem(), childId);
    }

    if (myId > 0) {
      myChildren = children;
    }

    return children;
  }

  @NotNull
  public InputStream getInputStream() throws IOException {
    return ourPersistence.getInputStream(this);
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    return ourPersistence.getOutputStream(this, requestor, modStamp, timeStamp);
  }

  public boolean isDirectory() {
    if (myIsDirectoryCached) return myIsDirectory;

    final boolean directory = ourPersistence.isDirectory(this);
    if (myId > 0) {
      myIsDirectory = directory;
      myIsDirectoryCached = true;
    }

    return directory;
  }

  public boolean isValid() {
    return exists();
  }

  @NotNull
  public Collection<VirtualFile> getCachedChildren() {
    final Map<String, VirtualFile> map = asMap();
    if (map != null) {
      Set<VirtualFile> files = new THashSet<VirtualFile>(map.values());
      files.remove(NullVirtualFile.INSTANCE);
      return files;
    }

    final VirtualFile[] a = asArray();
    if (a != null) return Arrays.asList(a);

    return Collections.emptyList();
  }

  public String toString() {
    return getUrl();
  }

  public void setName(final String newName) {
    myParent.removeChild(this);
    myName = newName;
    myParent.addChild(this);
  }

  public void setParent(final VirtualFile newParent) {
    myParent.removeChild(this);
    myParent = (VFileImpl)newParent;
    myParent.addChild(this);
  }

  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    myId = 0;
  }

  public Charset getCharset() {
    if (!isCharsetSet()) {
      try {
        LoadTextUtil.detectCharset(this, contentsToByteArray());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return super.getCharset();
  }

  @Nullable
  public VirtualFile[] asArray() {
    if (myChildren instanceof VirtualFile[]) return (VirtualFile[])myChildren;
    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  public Map<String, VirtualFile> asMap() {
    if (myChildren instanceof Map) return (Map<String, VirtualFile>)myChildren;
    return null;
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public Map<String, VirtualFile> ensureAsMap() {
    assert !(myChildren instanceof VirtualFile[]);

    if (myChildren == null) {
      myChildren = createMap();
    }

    return (Map<String, VirtualFile>)myChildren;
  }

  public void addChild(VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.append(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), file);
    }
  }

  public void removeChild(VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.remove(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), NullVirtualFile.INSTANCE);
    }
  }

  public boolean allChildrenLoaded() {
    return myChildren instanceof VirtualFile[];
  }

  public List<String> getSuspicousNames() {
    final Map<String, VirtualFile> map = asMap();
    if (map == null) return Collections.emptyList();

    List<String> names = new ArrayList<String>();
    for (String name : map.keySet()) {
      if (map.get(name) == NullVirtualFile.INSTANCE) {
        names.add(name);
      }
    }

    return names;
  }
}