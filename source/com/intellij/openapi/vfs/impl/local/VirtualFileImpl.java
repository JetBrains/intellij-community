package com.intellij.openapi.vfs.impl.local;

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.concurrency.WorkerThread;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;

public class VirtualFileImpl extends VirtualFile {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.VirtualFileImpl");

  private final LocalFileSystemImpl myFileSystem;

  private VirtualFileImpl myParent;
  private String myParentPath; // filled when myParent == null
  private String myName;
//  private String myExtension; // cached
//  private String myNameWithoutExtension; // cached
  private VirtualFileImpl[] myChildren = null;
  private boolean myDirectoryFlag; // null, if not defined yet
  private Boolean myWritableFlag = null; // null, if not defined yet
  private long myModificationStamp = LocalTimeCounter.currentTime();
  private long myTimeStamp = -1; // -1, if file content has not been requested yet

  private static final VirtualFileImpl[] EMPTY_VIRTUAL_FILE_ARRAY = new VirtualFileImpl[0];

  // used by tests
  public void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  private VirtualFileImpl(
    LocalFileSystemImpl fileSystem,
    VirtualFileImpl parent,
    PhysicalFile file,
    boolean isDirectory
    ) {
    myFileSystem = fileSystem;
    myParent = parent;
    setName(file.getName());
    if (myName.length() == 0){
      LOG.error("file:" + file.getPath());
    }
    myDirectoryFlag = isDirectory;
    if (!myDirectoryFlag) {
      myTimeStamp = file.lastModified();
    }
  }

  VirtualFileImpl(LocalFileSystemImpl fileSystem, String path) {
    myFileSystem = fileSystem;

    int lastSlash = path.lastIndexOf('/');
    LOG.assertTrue(lastSlash >= 0);
    if (lastSlash == path.length() - 1) { // 'c:/' or '/'
      myParentPath = null;
      setName(path);
      myDirectoryFlag = true;
    }
    else {
      int prevSlash = path.lastIndexOf('/', lastSlash - 1);
      if (prevSlash < 0) {
        myParentPath = path.substring(0, lastSlash + 1); // 'c:/' or '/'
        setName(path.substring(lastSlash + 1));
      }
      else {
        myParentPath = path.substring(0, lastSlash);
        setName(path.substring(lastSlash + 1));
      }
      myDirectoryFlag = getPhysicalFile().isDirectory();
    }
    LOG.assertTrue(myName.length() > 0);
  }

  boolean areChildrenCached() {
    synchronized (myFileSystem.LOCK) {
      return myChildren != null;
    }
  }

  void setParent(VirtualFileImpl parent) {
    synchronized (myFileSystem.LOCK) {
      myParent = parent;
      myParentPath = null;
    }
  }

  PhysicalFile getPhysicalFile() {
    String path = getPath(File.separatorChar);
    return new IoFile(path);  
  }

  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return getPath('/');
  }

  private String getPath(char separatorChar) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    int length = calcPathLength(false);
    StringBuffer buffer = new StringBuffer(length);
    appendPath(buffer, separatorChar);
    if (buffer.length() != length){
      LOG.error("buffer.length() != length",
                new String [] {
                  "length=" + length,
                  "buffer=" + buffer.toString()
                });
    }
    return buffer.toString();
  }

  private int calcPathLength(boolean addSepAfter) {
    int length = 0;
    synchronized (myFileSystem.LOCK) {
      if (myParent != null) {
        length += myParent.calcPathLength(true);
      }
      else {
        if (myParentPath != null) {
          length += myParentPath.length();
          if (!StringUtil.endsWithChar(myParentPath, '/')) {
            length++;
          }
        }
      }
    }
    length += myName.length();
    if (addSepAfter && !StringUtil.endsWithChar(myName, '/')) {
      length++;
    }
    return length;
  }

  private void appendPath(StringBuffer buffer, char separatorChar) {
    synchronized (myFileSystem.LOCK) {
      if (myParent == null) {
        if (myParentPath != null) {
          buffer.append(myParentPath.replace('/', separatorChar));
        }
      }
      else {
        myParent.appendPath(buffer, separatorChar);
      }
    }

    if (buffer.length() != 0 && buffer.charAt(buffer.length() - 1) != separatorChar) {
      buffer.append(separatorChar);
    }

    buffer.append(myName.replace('/', separatorChar));
  }

  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return (nameWithoutExtension.length () == 0 ? getName() : nameWithoutExtension);
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

    if (isInvalidName(newName)){
      throw new IOException("Invalid file name: \"" + newName + "\"");
    }

    myFileSystem.fireBeforePropertyChange(requestor, this, PROP_NAME, myName, newName);
    PhysicalFile file = getPhysicalFile();
    String oldName = myName;
    setName(newName);
    PhysicalFile newFile = getPhysicalFile();
    if (!file.renameTo(newFile)) {
      setName(file.getName());
      throw new IOException("Cannot rename file " + file.getPath() + " to " + newFile.getPath() + ".");
    }
    else {
      myFileSystem.firePropertyChanged(requestor, this, PROP_NAME, oldName, myName);
    }
  }

  public boolean isWritable() {
    synchronized (myFileSystem.LOCK) {
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
    synchronized (myFileSystem.LOCK) {
      if (myParent != null) {
        return myParent.isValid();
      }
      else {
        return myFileSystem.isRoot(this);
      }
    }
  }

  public VirtualFile getParent() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    synchronized (myFileSystem.LOCK) {
      if (myParent != null) {
        return myParent;
      }
      else {
        if (myParentPath == null) return null;
        return myFileSystem.findFileByPath(myParentPath);
      }
    }
  }

  public VirtualFile[] getChildren() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (!isDirectory()) return null;
    synchronized (myFileSystem.LOCK) {
      if (myChildren == null) {
        ArrayList<VirtualFile> array = new ArrayList<VirtualFile>();
        PhysicalFile file = getPhysicalFile();
        PhysicalFile[] files = file.listFiles();
        if (files != null) {
          for (int i = 0; i < files.length; i++) {
            PhysicalFile f = files[i];
            array.add(new VirtualFileImpl(myFileSystem, this, f, f.isDirectory()));
          }
        }
        myChildren = array.toArray(EMPTY_VIRTUAL_FILE_ARRAY);
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
      throw new IOException();
    }

    if (isInvalidName(name)){
      throw new IOException("Invalid file name: \"" + name + "\"");
    }

    PhysicalFile physicalFile = getPhysicalFile().createChild(name);
    VirtualFile file = findChild(name);
    if (file != null || physicalFile.exists()) {
      throw new IOException("Cannot create file " + physicalFile.getPath() + ". File already exists.");
    }
    if (!physicalFile.mkdir()) {
      throw new IOException("Cannot create file " + physicalFile.getPath() + ".");
    }
    VirtualFileImpl child = new VirtualFileImpl(myFileSystem, this, physicalFile, true);
    addChild(child);
    myFileSystem.fireFileCreated(requestor, child);
    return child;
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    if (!isDirectory()) {
      throw new IOException();
    }

    if (isInvalidName(name)){
      throw new IOException("Invalid file name: \"" + name + "\"");
    }

    PhysicalFile physicalFile = getPhysicalFile().createChild(name);
    VirtualFile file = findChild(name);
    if (file != null || physicalFile.exists()) {
      throw new IOException("Cannot create file " + physicalFile.getPath() + ". File already exists.");
    }
    physicalFile.createOutputStream().close();
    VirtualFileImpl child = new VirtualFileImpl(myFileSystem, this, physicalFile, false);
    addChild(child);
    myFileSystem.fireFileCreated(requestor, child);
    return child;
  }

  public void delete(Object requestor) throws IOException {
    LOG.assertTrue(isValid());

    PhysicalFile physicalFile = getPhysicalFile();
    VirtualFileImpl parent = (VirtualFileImpl)getParent();
    if (parent == null) {
      throw new IOException("Cannot delete root file " + physicalFile.getPath() + ".");
    }

    myFileSystem.fireBeforeFileDeletion(requestor, this);

    boolean isDirectory = isDirectory();
    delete(physicalFile);

    parent.removeChild(this);
    myFileSystem.fireFileDeleted(requestor, this, myName, isDirectory, parent);
  }

  private static void delete(PhysicalFile physicalFile) throws IOException {
    PhysicalFile[] list = physicalFile.listFiles();
    if (list != null) {
      for (int i = 0; i < list.length; i++) {
        delete(list[i]);
      }
    }
    if (!physicalFile.delete()) {
      throw new IOException("Cannot delete file " + physicalFile.getPath() + ".");
    }
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
    if (!(newParent instanceof VirtualFileImpl)) {
      throw new IOException("Can not move file to " + newParent.getPresentableUrl());
    }

    String name = getName();
    VirtualFileImpl oldParent = myParent;
    myFileSystem.fireBeforeFileMovement(requestor, this, newParent);

    PhysicalFile physicalFile = getPhysicalFile();
    PhysicalFile newPhysicalParent = ((VirtualFileImpl)newParent).getPhysicalFile();
    PhysicalFile newPhysicalFile = newPhysicalParent.createChild(name);
    if (!physicalFile.renameTo(newPhysicalFile)) {
      throw new IOException("Cannot move file " + physicalFile.getPath() + " to " + newPhysicalParent.getPath() + ".");
    }

    oldParent.removeChild(this);

    myParent = (VirtualFileImpl)newParent;
    ((VirtualFileImpl)newParent).addChild(this);
    //myModificationStamp = LocalTimeCounter.currentTime();
    //myTimeStamp = -1;
    myFileSystem.fireFileMoved(requestor, this, oldParent);
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
      throw new IOException("Cannot read from file " + getPhysicalFile().getPath() + ".");
    }

    if (myTimeStamp < 0) return physicalContent();

    ProvidedContent content = myFileSystem.getManager().getProvidedContent(this);
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
      throw new IOException("Cannot write to file " + physicalFile.getPath() + ".");
    }
    myFileSystem.fireBeforeContentsChange(requestor, this);
    final OutputStream out = new BufferedOutputStream(physicalFile.createOutputStream());
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
        myFileSystem.fireContentsChanged(requestor, VirtualFileImpl.this, oldModificationStamp);
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    InputStream in = getInputStream();
    byte[] bytes = new byte[(int)getLength()];
    try {
      int count = 0;
      for (; ;) {
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
    if (asynchronous) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    else {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    final ModalityState modalityState = EventQueue.isDispatchThread() ? ModalityState.current() : ModalityState.NON_MMODAL;

    if (LOG.isDebugEnabled()) {
      LOG.debug("VirtualFile.refresh():" + getPresentableUrl() + ", recursive = " + recursive + ", modalityState = " + modalityState);
    }

    final WorkerThread worker;
    if (asynchronous) {
      worker = new WorkerThread("Synchronize worker");
    }
    else {
      worker = null;
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        myFileSystem.getManager().beforeRefreshStart(asynchronous, modalityState, postRunnable);

        PhysicalFile physicalFile = getPhysicalFile();
        if (!physicalFile.exists()) {
          Runnable runnable = new Runnable() {
            public void run() {
              if (!isValid()) return;
              VirtualFileImpl parent = (VirtualFileImpl)getParent();
              if (parent != null) {
                myFileSystem.fireBeforeFileDeletion(null, VirtualFileImpl.this);
                parent.removeChild(VirtualFileImpl.this);
                myFileSystem.fireFileDeleted(null, VirtualFileImpl.this, myName, myDirectoryFlag, parent);
              }
            }
          };
          myFileSystem.getManager().addEventToFireByRefresh(runnable, asynchronous, modalityState);
        }
        else {
          myFileSystem.refresh(VirtualFileImpl.this, recursive, true, worker, modalityState);
        }
      }
    };

    final Runnable endTask = new Runnable() {
      public void run() {
        myFileSystem.getManager().afterRefreshFinish(asynchronous, modalityState);
      }
    };

    if (asynchronous) {
      Runnable runnable1 = new Runnable() {
        public void run() {
          LOG.info("Executing request:" + this);

          final ProgressIndicator indicator = myFileSystem.getManager().getRefreshIndicator();
          if (indicator != null) {
            indicator.start();
            indicator.setText("Synchronizing files...");
          }

          worker.start();
          ApplicationManager.getApplication().runReadAction(runnable);
          worker.dispose(false);
          try {
            worker.join();
          }
          catch (InterruptedException e) {
          }

          if (indicator != null) {
            indicator.stop();
          }

          endTask.run();
        }
      };
      myFileSystem.getSynchronizeQueueAlarm().addRequest(runnable1, 0);
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

  // should not check if file exists - already checked
  void refreshInternal(final boolean recursive, final WorkerThread worker, final ModalityState modalityState) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshInternal recursive = " + recursive + " worker = " + worker + " " + myName);
    }

    PhysicalFile physicalFile = getPhysicalFile();

    final boolean isDirectory = physicalFile.isDirectory();
    if (isDirectory != myDirectoryFlag) {
      final PhysicalFile _physicalFile = physicalFile;
      myFileSystem.getManager().addEventToFireByRefresh(
        new Runnable() {
          public void run() {
            if (!isValid()) return;
            VirtualFileImpl parent = (VirtualFileImpl)getParent();
            if (parent == null) return;

            myFileSystem.fireBeforeFileDeletion(null, VirtualFileImpl.this);
            parent.removeChild(VirtualFileImpl.this);
            myFileSystem.fireFileDeleted(null, VirtualFileImpl.this, myName, myDirectoryFlag, parent);
            VirtualFileImpl newChild = new VirtualFileImpl(myFileSystem, parent, _physicalFile, isDirectory);
            parent.addChild(newChild);
            myFileSystem.fireFileCreated(null, newChild);
          }
        },
        worker != null,
        modalityState
      );
      return;
    }

    if (isDirectory) {
      if (myChildren == null) return;
      PhysicalFile[] files = physicalFile.listFiles();
      if (files == null) {
        files = new PhysicalFile[0]; //?
      }
      boolean[] found = new boolean[myChildren.length];
      VirtualFileImpl[] children = myChildren;
      for (int i = 0; i < files.length; i++) {
        final PhysicalFile file = files[i];
        final String name = file.getName();
        int index = -1;
        // optimization: first check at expected index
        if (i < children.length && children[i].myName.equals(name)) {
          index = i;
        }
        else {
          for (int j = 0; j < children.length; j++) {
            if (children[j].myName.equals(name)) {
              index = j;
              break;
            }
          }
        }
        if (index < 0) {
          myFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (VirtualFileImpl.this.isValid()) {
                  if (findChild(file.getName()) != null) return; // was already created
                  VirtualFileImpl newChild = new VirtualFileImpl(
                    myFileSystem,
                    VirtualFileImpl.this,
                    file,
                    file.isDirectory()
                  );
                  addChild(newChild);
                  myFileSystem.fireFileCreated(null, newChild);
                }
              }
            },
            worker != null,
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
            if (worker != null) {
              worker.addTask(
                new Runnable() {
                  public void run() {
                    Runnable action = new Runnable() {
                      public void run() {
                        child.refreshInternal(recursive, worker, modalityState);
                      }
                    };
                    ApplicationManager.getApplication().runReadAction(action);
                  }
                }
              );
            }
            else {
              child.refreshInternal(recursive, null, modalityState);
            }
          }
        }
        else {
          myFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (child.isValid()) {
                  myFileSystem.fireBeforeFileDeletion(null, child);
                  removeChild(child);
                  myFileSystem.fireFileDeleted(null, child, child.myName, child.myDirectoryFlag, VirtualFileImpl.this);
                }
              }
            },
            worker != null,
            modalityState
          );
        }
      }
    }
    else {
      if (myTimeStamp > 0) {
        final long timeStamp = physicalFile.lastModified();
        if (timeStamp != myTimeStamp) {
          myFileSystem.getManager().addEventToFireByRefresh(
            new Runnable() {
              public void run() {
                if (!isValid()) return;

                myFileSystem.fireBeforeContentsChange(null, VirtualFileImpl.this);
                long oldModificationStamp = getModificationStamp();
                myTimeStamp = timeStamp;
                myModificationStamp = LocalTimeCounter.currentTime();
                myFileSystem.fireContentsChanged(null, VirtualFileImpl.this, oldModificationStamp);
              }
            },
            worker != null,
            modalityState
          );
        }
      }
    }

    if (myWritableFlag != null) {
      final boolean isWritable = isWritable(physicalFile, isDirectory());
      if (isWritable != myWritableFlag.booleanValue()) {
        myFileSystem.getManager().addEventToFireByRefresh(
          new Runnable() {
            public void run() {
              if (!isValid()) return;

              myFileSystem.fireBeforePropertyChange(
                null, VirtualFileImpl.this, PROP_WRITABLE,
                myWritableFlag, isWritable ? Boolean.TRUE : Boolean.FALSE
              );
              myWritableFlag = isWritable ? Boolean.TRUE : Boolean.FALSE;
              myFileSystem.firePropertyChanged(
                null, VirtualFileImpl.this, PROP_WRITABLE,
                isWritable ? Boolean.FALSE : Boolean.TRUE, myWritableFlag
              );
            }
          },
          worker != null,
          modalityState
        );
      }
    }
  }


  private void addChild(VirtualFileImpl child) {
    getChildren(); // to initialize myChildren

    VirtualFileImpl[] newChildren = new VirtualFileImpl[myChildren.length + 1];
    System.arraycopy(myChildren, 0, newChildren, 0, myChildren.length);
    newChildren[myChildren.length] = child;
    myChildren = newChildren;
  }

  private void removeChild(VirtualFileImpl child) {
    getChildren(); // to initialize myChildren

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

  private boolean isInvalidName(String name){
    if (name.indexOf('\\') >= 0) return true;
    if (name.indexOf('/') >= 0) return true;
    return false;
  }

  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  private void setName(String name) {
    myName = new String(name);
  }
}
