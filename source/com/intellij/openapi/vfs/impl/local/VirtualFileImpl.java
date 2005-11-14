package com.intellij.openapi.vfs.impl.local;

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;

public class VirtualFileImpl extends VirtualFile {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.VirtualFileImpl");

  private static final LocalFileSystemImpl ourFileSystem = (LocalFileSystemImpl)LocalFileSystem.getInstance();

  private VirtualFileImpl myParent;
  private String myName;
  private VirtualFileImpl[] myChildren = null;  // null, if not defined yet
  private boolean myDirectoryFlag;
  private Boolean myWritableFlag = null; // null, if not defined yet
  private long myModificationStamp = LocalTimeCounter.currentTime();
  private long myTimeStamp = -1; // -1, if file content has not been requested yet

  private static final VirtualFileImpl[] EMPTY_VIRTUAL_FILE_ARRAY = new VirtualFileImpl[0];

  // used by tests
  public void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  private VirtualFileImpl(
    VirtualFileImpl parent,
    PhysicalFile file,
    boolean isDirectory
  ) {
    myParent = parent;
    setName(file.getName());
    if (myName.length() == 0) {
      LOG.error("file:" + file.getPath());
    }
    myDirectoryFlag = isDirectory;
    if (!myDirectoryFlag) {
      myTimeStamp = file.lastModified();
    }
  }

  //for constructing roots
  VirtualFileImpl(String path) {
    int lastSlash = path.lastIndexOf('/');
    LOG.assertTrue(lastSlash >= 0);
    if (lastSlash == path.length() - 1) { // 'c:/' or '/'
      setName(path);
      myDirectoryFlag = true;
    }
    else {
      setName(path.substring(lastSlash + 1));
      String systemPath = path.replace('/', File.separatorChar);
      myDirectoryFlag = new IoFile(systemPath).isDirectory();
    }
    LOG.assertTrue(myName.length() > 0);
  }

  boolean areChildrenCached() {
    synchronized (ourFileSystem.LOCK) {
      return myChildren != null;
    }
  }

  private void setParent(VirtualFileImpl parent) {
    synchronized (ourFileSystem.LOCK) {
      myParent = parent;
    }
  }

  PhysicalFile getPhysicalFile() {
    String path = getPath(File.separatorChar, 1024);
    return new IoFile(path);
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  public String getPath() {
    return getPath('/', 1024);
  }

  private String getPath(char separatorChar, int bufferLength) {
    //ApplicationManager.getApplication().assertReadAccessAllowed();
    try {
      char[] buffer = new char[bufferLength];
      int length = 0;
      synchronized (ourFileSystem.LOCK) {
        length = appendPath(buffer, separatorChar, 0);
      }
      return StringFactory.createStringFromConstantArray(buffer, 0, length);
    }
    catch(ArrayIndexOutOfBoundsException aiob) {
      return getPath(separatorChar, bufferLength * 2);
    }
  }

  private int appendPath(char[] buffer, char separatorChar, int currentLength) {
    if (myParent != null)
      currentLength = myParent.appendPath(buffer, separatorChar, 0);

    if (currentLength > 0 && buffer[currentLength - 1] != separatorChar)
      buffer[currentLength++] = separatorChar;
    final int nameLength = myName.length();
    myName.replace('/', separatorChar).getChars(0, nameLength, buffer, currentLength);
    return currentLength + nameLength;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.length() == 0 ? getName() : nameWithoutExtension;
    }
    return getName();
  }

  public void rename(Object requestor, String newName) throws IOException {
    if (getParent() == null) {
      return;
    }
    if (myName.equals(newName)) {
      return;
    }

    if (isInvalidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    final boolean auxCommand = ourFileSystem.auxRename(this, newName);

    ourFileSystem.fireBeforePropertyChange(requestor, this, PROP_NAME, myName, newName);

    String oldName = myName;
    if (!auxCommand) {
      PhysicalFile file = getPhysicalFile();
      setName(newName);
      PhysicalFile newFile = getPhysicalFile();
      if (!file.renameTo(newFile)) {
        setName(file.getName());
        throw new IOException(VfsBundle.message("file.rename.error", file.getPath(), newFile.getPath()));
      }
    }
    else {
      setName(newName);
    }

    ourFileSystem.firePropertyChanged(requestor, this, PROP_NAME, oldName, myName);
  }

  public boolean isWritable() {
    synchronized (ourFileSystem.LOCK) {
      if (myWritableFlag == null) {
        myWritableFlag = isWritable(getPhysicalFile(), isDirectory()) ? Boolean.TRUE : Boolean.FALSE;
      }
    }
    return myWritableFlag.booleanValue();
  }

  private static boolean isWritable(PhysicalFile physicalFile, boolean isDirectory) {
    if (Patches.ALL_FOLDERS_ARE_WRITABLE && isDirectory) {
      return true;
    }
    else {
      return physicalFile.canWrite();
    }
  }

  public boolean isDirectory() {
    return myDirectoryFlag;
  }

  public boolean isValid() {
    synchronized (ourFileSystem.LOCK) {
      if (myParent == null) {
        return ourFileSystem.isRoot(this);
      }

      return myParent.isValid();
    }
  }

  @Nullable
  public VirtualFile getParent() {
    synchronized (ourFileSystem.LOCK) {
      return myParent;
    }
  }

  public VirtualFile[] getChildren() {
    if (!isDirectory()) return null;
    synchronized (ourFileSystem.LOCK) {
      if (myChildren == null) {
        PhysicalFile file = getPhysicalFile();
        PhysicalFile[] files = file.listFiles();
        final int length = files.length;
        if (length == 0) {
          myChildren = EMPTY_VIRTUAL_FILE_ARRAY;
        }
        else {
          myChildren = new VirtualFileImpl[ length ];
          for (int i = 0; i < length; ++i) {
            PhysicalFile f = files[i];
            myChildren[i] = new VirtualFileImpl(this, f, f.isDirectory());
          }
        }
      }
    }
    return myChildren;
  }

  void replaceChild(VirtualFileImpl oldChild, VirtualFileImpl newChild) {
    for (int i = 0; i < myChildren.length; i++) {
      VirtualFileImpl child = myChildren[i];
      if (child == oldChild) {
        myChildren[i] = newChild;
        return;
      }
    }
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VfsBundle.message("directory.create.wrong.parent.error"));
    }

    if (isInvalidName(name)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", name));
    }

    VirtualFile existingFile = findChild(name);

    final boolean auxCommand = ourFileSystem.auxCreateDirectory(this, name);

    PhysicalFile physicalFile = getPhysicalFile().createChild(name);

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

    VirtualFileImpl child = new VirtualFileImpl(this, physicalFile, true);
    addChild(child);
    ourFileSystem.fireFileCreated(requestor, child);
    return child;
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException(VfsBundle.message("directory.create.wrong.parent.error"));
    }

    if (isInvalidName(name)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", name));
    }

    final boolean auxCommand = ourFileSystem.auxCreateFile(this, name);

    PhysicalFile physicalFile = getPhysicalFile().createChild(name);
    if (!auxCommand) {
      VirtualFile file = findChild(name);
      if (file != null || physicalFile.exists()) {
        throw new IOException(VfsBundle.message("file.already.exists.error", physicalFile.getPath()));
      }
      physicalFile.createOutputStream().close();
    }

    VirtualFileImpl child = new VirtualFileImpl(this, physicalFile, false);
    addChild(child);
    ourFileSystem.fireFileCreated(requestor, child);
    return child;
  }

  public void delete(Object requestor) throws IOException {
    LOG.assertTrue(isValid());

    PhysicalFile physicalFile = getPhysicalFile();
    VirtualFileImpl parent = (VirtualFileImpl)getParent();
    if (parent == null) {
      throw new IOException(VfsBundle.message("file.delete.root.error", physicalFile.getPath()));
    }

    final boolean auxCommand = ourFileSystem.auxDelete(this);

    ourFileSystem.fireBeforeFileDeletion(requestor, this);

    boolean isDirectory = isDirectory();

    if (!auxCommand) {
      delete(physicalFile);
    }

    parent.removeChild(this);
    ourFileSystem.fireFileDeleted(requestor, this, myName, isDirectory, parent);

    if (auxCommand && isDirectory && physicalFile.exists()) {
      // Some auxHandlers refuse to delete directories actually as per version controls like CVS or SVN.
      // So if the direcotry haven't been deleted actually we must recreate VFS structure for this.
      VirtualFileImpl newMe = new VirtualFileImpl(parent, physicalFile, true);
      parent.addChild(newMe);
      ourFileSystem.fireFileCreated(requestor, newMe);
    }
  }

  private static void delete(PhysicalFile physicalFile) throws IOException {
    PhysicalFile[] list = physicalFile.listFiles();
    for (PhysicalFile aList : list) {
      delete(aList);
    }
    if (!physicalFile.delete()) {
      throw new IOException(VfsBundle.message("file.delete.error", physicalFile.getPath()));
    }
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
    if (!(newParent instanceof VirtualFileImpl)) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    String name = getName();
    VirtualFileImpl oldParent = myParent;
    final boolean auxCommand = ourFileSystem.auxMove(this, newParent);

    ourFileSystem.fireBeforeFileMovement(requestor, this, newParent);

    newParent.getChildren(); // Init children.

    PhysicalFile physicalFile = getPhysicalFile();
    boolean isDirectory = isDirectory();

    if (!auxCommand) {
      PhysicalFile newPhysicalParent = ((VirtualFileImpl)newParent).getPhysicalFile();
      PhysicalFile newPhysicalFile = newPhysicalParent.createChild(name);
      if (!physicalFile.renameTo(newPhysicalFile)) {
        throw new IOException(VfsBundle.message("file.move.to.error", physicalFile.getPath(), newPhysicalParent.getPath()));
      }
    }

    oldParent.removeChild(this);

    myParent = (VirtualFileImpl)newParent;
    ((VirtualFileImpl)newParent).addChild(this);
    //myModificationStamp = LocalTimeCounter.currentTime();
    //myTimeStamp = -1;
    ourFileSystem.fireFileMoved(requestor, this, oldParent);

    if (auxCommand && isDirectory && physicalFile.exists()) {
      // Some auxHandlers refuse to delete directories actually as per version controls like CVS or SVN.
      // So if the direcotry haven't been deleted actually we must recreate VFS structure for this.
      VirtualFileImpl newMe = new VirtualFileImpl(oldParent, physicalFile, true);
      oldParent.addChild(newMe);
      ourFileSystem.fireFileCreated(requestor, newMe);
    }
  }

  public InputStream getInputStream() throws IOException {
    return getProvidedContent().getInputStream();
  }

  public long getLength() {
    LOG.assertTrue(!isDirectory());
    ProvidedContent content;
    try {
      content = getProvidedContent();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return content.getLength();
  }


  private ProvidedContent getProvidedContent() throws IOException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (isDirectory()) {
      throw new IOException(VfsBundle.message("file.read.error", getPhysicalFile().getPath()));
    }

    if (myTimeStamp < 0) return physicalContent();

    ProvidedContent content = ourFileSystem.getManager().getProvidedContent(this);
    return content == null ? physicalContent() : content;

  }

  private ProvidedContent physicalContent() {
    return new ProvidedContent() {
      public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(getPhysicalFileInputStream());
      }

      public int getLength() {
        return getPhysicalFileLength();
      }
    };
  }

  private InputStream getPhysicalFileInputStream() throws IOException {
    getTimeStamp();
    return getPhysicalFile().createInputStream();
  }

  public OutputStream getOutputStream(final Object requestor,
                                      final long newModificationStamp,
                                      final long newTimeStamp) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    PhysicalFile physicalFile = getPhysicalFile();
    if (isDirectory()) {
      throw new IOException(VfsBundle.message("file.write.error", physicalFile.getPath()));
    }
    ourFileSystem.fireBeforeContentsChange(requestor, this);
    final OutputStream out = new BufferedOutputStream(physicalFile.createOutputStream());
    if (myBOM != null) {
      out.write(myBOM);
    }
    return new OutputStream() {
      public void write(int b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      public void flush() throws IOException {
        out.flush();
      }

      public void close() throws IOException {
        out.close();
        long oldModificationStamp = getModificationStamp();
        myModificationStamp = newModificationStamp >= 0 ? newModificationStamp : LocalTimeCounter.currentTime();
        if (newTimeStamp >= 0) {
          getPhysicalFile().setLastModified(newTimeStamp);
        }
        myTimeStamp = getPhysicalFile().lastModified();
        ourFileSystem.fireContentsChanged(requestor, VirtualFileImpl.this, oldModificationStamp);
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    InputStream in = getInputStream();
    byte[] bytes = new byte[(int)getLength()];
    try {
      int count = 0;
      while (true) {
        int n = in.read(bytes, count, bytes.length - count);
        if (n <= 0) break;
        count += n;
      }
    }
    finally {
      in.close();
    }
    return bytes;
  }

  public char[] contentsToCharArray() throws IOException {
    Reader reader = getReader();
    char[] chars;
    try {
      chars = FileUtil.loadText(reader, (int)getLength());
    }
    finally {
      reader.close();
    }
    return chars;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public long getTimeStamp() {
    if (myTimeStamp < 0) {
      myTimeStamp = getPhysicalFile().lastModified();
    }
    return myTimeStamp;
  }

  public long getActualTimeStamp() {
    return getPhysicalFile().lastModified();
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    final ModalityState modalityState = EventQueue.isDispatchThread() ? ModalityState.current() : ModalityState.NON_MMODAL;

    if (LOG.isDebugEnabled()) {
      LOG.debug("VirtualFile.refresh():" + getPresentableUrl() + ", recursive = " + recursive + ", modalityState = " + modalityState);
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        ourFileSystem.getManager().beforeRefreshStart(asynchronous, modalityState, postRunnable);

        PhysicalFile physicalFile = getPhysicalFile();
        if (!physicalFile.exists()) {
          Runnable runnable = new Runnable() {
            public void run() {
              if (!isValid()) return;
              VirtualFileImpl parent = (VirtualFileImpl)getParent();
              if (parent != null) {
                ourFileSystem.fireBeforeFileDeletion(null, VirtualFileImpl.this);
                parent.removeChild(VirtualFileImpl.this);
                ourFileSystem.fireFileDeleted(null, VirtualFileImpl.this, myName, myDirectoryFlag, parent);
              }
            }
          };
          ourFileSystem.getManager().addEventToFireByRefresh(runnable, asynchronous, modalityState);
        }
        else {
          ourFileSystem.refresh(VirtualFileImpl.this, recursive, true, modalityState, asynchronous, false);
        }
      }
    };

    final Runnable endTask = new Runnable() {
      public void run() {
        ourFileSystem.getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };

    if (asynchronous) {
      Runnable runnable1 = new Runnable() {
        public void run() {
          LOG.info("Executing request:" + this);

          final ProgressIndicator indicator = ourFileSystem.getManager().getRefreshIndicator();
          indicator.start();
          indicator.setText(VfsBundle.message("file.synchronize.progress"));

          ApplicationManager.getApplication().runReadAction(runnable);

          indicator.stop();

          endTask.run();
        }
      };

      ourFileSystem.getSynchronizeExecutor().submit(runnable1);
    }
    else {
      runnable.run();
      endTask.run();
    }
  }

  public boolean nameEquals(String name) {
    return SystemInfo.isFileSystemCaseSensitive ? getName().equals(name) : getName().equalsIgnoreCase(name);
  }

  public byte[] physicalContentsToByteArray() throws IOException {
    InputStream inputStream = getPhysicalFileInputStream();
    try {
      int physicalFileLength = getPhysicalFileLength();
      LOG.assertTrue(physicalFileLength >= 0);
      return FileUtil.loadBytes(inputStream, physicalFileLength);
    }
    finally {
      inputStream.close();
    }
  }

  public int getPhysicalFileLength() {
    return (int)getPhysicalFile().length();
  }

  void refreshInternal(final boolean recursive,
                       final ModalityState modalityState,
                       final boolean forceRefresh,
                       final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    if (!isValid()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshInternal recursive = " + recursive + " asynchronous = " + asynchronous + " file = " + myName);
    }

    PhysicalFile physicalFile = getPhysicalFile();

    final boolean isDirectory = physicalFile.isDirectory();
    if (isDirectory != myDirectoryFlag) {
      final PhysicalFile _physicalFile = physicalFile;
      ourFileSystem.getManager().addEventToFireByRefresh(
        new Runnable() {
          public void run() {
            if (!isValid()) return;
            VirtualFileImpl parent = (VirtualFileImpl)getParent();
            if (parent == null) return;

            ourFileSystem.fireBeforeFileDeletion(null, VirtualFileImpl.this);
            parent.removeChild(VirtualFileImpl.this);
            ourFileSystem.fireFileDeleted(null, VirtualFileImpl.this, myName, myDirectoryFlag, parent);
            VirtualFileImpl newChild = new VirtualFileImpl(parent, _physicalFile, isDirectory);
            parent.addChild(newChild);
            ourFileSystem.fireFileCreated(null, newChild);
          }
        },
        asynchronous,
        modalityState
      );
      return;
    }

    if (isDirectory) {
      if (myChildren == null) return;
      PhysicalFile[] files = physicalFile.listFiles();

      final boolean[] found = new boolean[myChildren.length];

      VirtualFileImpl[] children = myChildren;
      for (int i = 0; i < files.length; i++) {
        final PhysicalFile file = files[i];
        final String name = file.getName();
        int index = -1;
        if (i < children.length && children[i].myName.equals(name)) {
          index = i;
        } else {
          for (int j = 0; j < children.length; j++) {
            VirtualFileImpl child = myChildren[j];
            if (child.myName.equals(name)) index = j;
          }
        }

        if (index < 0) {
          ourFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (VirtualFileImpl.this.isValid()) {
                  if (findChild(file.getName()) != null) return; // was already created
                  VirtualFileImpl newChild = new VirtualFileImpl(
                    VirtualFileImpl.this,
                    file,
                    file.isDirectory()
                  );
                  addChild(newChild);
                  ourFileSystem.fireFileCreated(null, newChild);
                }
              }
            },
            asynchronous,
            modalityState
          );
        }
        else {
          found[index] = true;
        }
      }
      for (int i = 0; i < children.length; i++) {
        final VirtualFileImpl child = children[i];
        if (found[i]) {
          if (recursive) {
            child.refreshInternal(recursive, modalityState, false, asynchronous);
          }
        }
        else {
          ourFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (child.isValid()) {
                  ourFileSystem.fireBeforeFileDeletion(null, child);
                  removeChild(child);
                  ourFileSystem.fireFileDeleted(null, child, child.myName, child.myDirectoryFlag, VirtualFileImpl.this);
                }
              }
            },
            asynchronous,
            modalityState
          );
        }
      }
    }
    else {
      if (myTimeStamp > 0) {
        final long timeStamp = physicalFile.lastModified();
        if (timeStamp != myTimeStamp || forceRefresh) {
          ourFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (!isValid()) return;

                ourFileSystem.fireBeforeContentsChange(null, VirtualFileImpl.this);
                long oldModificationStamp = getModificationStamp();
                myTimeStamp = timeStamp;
                myModificationStamp = LocalTimeCounter.currentTime();
                ourFileSystem.fireContentsChanged(null, VirtualFileImpl.this, oldModificationStamp);
              }
            },
            asynchronous,
            modalityState
          );
        }
      }
    }

    if (myWritableFlag != null) {
      final boolean isWritable = isWritable(physicalFile, isDirectory());
      if (isWritable != myWritableFlag.booleanValue()) {
        ourFileSystem.getManager().addEventToFireByRefresh(
          new Runnable() {
            public void run() {
              if (!isValid()) return;

              ourFileSystem.fireBeforePropertyChange(
                null, VirtualFileImpl.this, PROP_WRITABLE,
                myWritableFlag, isWritable ? Boolean.TRUE : Boolean.FALSE
              );
              myWritableFlag = isWritable ? Boolean.TRUE : Boolean.FALSE;
              ourFileSystem.firePropertyChanged(
                null, VirtualFileImpl.this, PROP_WRITABLE,
                isWritable ? Boolean.FALSE : Boolean.TRUE, myWritableFlag
              );
            }
          },
          asynchronous,
          modalityState
        );
      }
    }
  }


  void addChild(VirtualFileImpl child) {
    getChildren(); // to initialize myChildren

    synchronized (ourFileSystem.LOCK) {
      VirtualFileImpl[] newChildren = new VirtualFileImpl[myChildren.length + 1];
      System.arraycopy(myChildren, 0, newChildren, 0, myChildren.length);
      newChildren[myChildren.length] = child;
      myChildren = newChildren;
      child.setParent(this);
    }
  }

  void removeChild(VirtualFileImpl child) {
    getChildren(); // to initialize myChildren

    synchronized (ourFileSystem.LOCK) {
      for (int i = 0; i < myChildren.length; i++) {
        if (myChildren[i] == child) {
          VirtualFileImpl[] newChildren = new VirtualFileImpl[myChildren.length - 1];
          System.arraycopy(myChildren, 0, newChildren, 0, i);
          System.arraycopy(myChildren, i + 1, newChildren, i, newChildren.length - i);
          myChildren = newChildren;
          child.myParent = null;
          return;
        }
      }
    }
  }

  private static boolean isInvalidName(String name) {
    if (name.indexOf('\\') >= 0) return true;
    return name.indexOf('/') >= 0;
  }

  @NonNls
  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  private void setName(String name) {
    myName = name;
  }
}
