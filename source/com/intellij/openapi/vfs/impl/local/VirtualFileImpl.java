package com.intellij.openapi.vfs.impl.local;

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
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

  private static char[] ourBuffer = new char[1024];

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

  VirtualFileImpl(
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
      setName(path.substring(0, lastSlash));
      myDirectoryFlag = true;
    }
    else {
      setName(path.substring(lastSlash + 1));
      String systemPath = path.replace('/', File.separatorChar);
      myDirectoryFlag = new IoFile(systemPath).isDirectory();
    }
  }

  boolean areChildrenCached() {
    synchronized (ourFileSystem.LOCK) {
      return myChildren != null;
    }
  }

  void setParent(VirtualFileImpl parent) {
    synchronized (ourFileSystem.LOCK) {
      myParent = parent;
    }
  }

  PhysicalFile getPhysicalFile() {
    String path = getPath(File.separatorChar);
    if (ourFileSystem.isRoot(this)) {
      path += "/";
    }
    return new IoFile(path);
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  public String getPath() {
    return getPath('/');
  }

  private String getPath(char separatorChar) {
    //ApplicationManager.getApplication().assertReadAccessAllowed();
    synchronized (ourFileSystem.LOCK) {
      try {
        int length = appendPath(ourBuffer, separatorChar);
        return new String(ourBuffer, 0, length);
      }
      catch (ArrayIndexOutOfBoundsException aiob) {
        ourBuffer = new char[ourBuffer.length * 2];
        return getPath(separatorChar);
      }
    }
  }

  private int appendPath(char[] buffer, char separatorChar) {
    int currentLength;
    if (myParent != null) {
      currentLength = myParent.appendPath(buffer, separatorChar);
      buffer[currentLength++] = separatorChar;
    } else {
      currentLength = 0;
    }

    final int nameLength = myName.length();
    myName.getChars(0, nameLength, buffer, currentLength);
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
  public VirtualFileImpl getParent() {
    synchronized (ourFileSystem.LOCK) {
      return myParent;
    }
  }

  public VirtualFile[] getChildren() {
    if (!isDirectory()) return null;
    if (myChildren == null) {
      synchronized (ourFileSystem.LOCK) {
        if (myChildren == null) {
          PhysicalFile file = getPhysicalFile();
          PhysicalFile[] files = file.listFiles();
          final int length = files == null ? 0 : files.length;
          if (length == 0) {
            myChildren = EMPTY_VIRTUAL_FILE_ARRAY;
          }
          else {
            myChildren = new VirtualFileImpl[ length ];
            String path = getPath() + "/";
            for (int i = 0; i < length; ++i) {
              PhysicalFile f = files[i];
              String childPath = path + f.getName();
              VirtualFileImpl child = ourFileSystem.myUnaccountedFiles.remove(childPath);
              if (child == null || !child.isValid()) {
                child = new VirtualFileImpl(this, f, f.isDirectory());
              }
              myChildren[i] = child;
            }
          }
        }
      }
    }
    return myChildren;
  }

  VirtualFile findSingleChild(String name) {

    if (!isDirectory()) return null;
    if (myChildren != null) return super.findChild(name);
    synchronized (ourFileSystem.LOCK) {
      String path = getPath() + "/" + name;
      VirtualFileImpl child = ourFileSystem.myUnaccountedFiles.get(path);
      if (child != null) {
        if (child.isValid()) {
          return child;
        } else {
          ourFileSystem.myUnaccountedFiles.remove(path);
        }
      }

      PhysicalFile physicalFile = new IoFile(path);
      if (physicalFile.exists()) {
        child = new VirtualFileImpl(this, physicalFile, physicalFile.isDirectory());
        ourFileSystem.myUnaccountedFiles.put(path, child);
        return child;
      } else {
        ourFileSystem.myUnaccountedFiles.put(path, null);
      }
    }

    return null;
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

  protected InputStream getPhysicalFileInputStream() throws IOException {
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
    if (getBOM() != null) {
      out.write(getBOM());
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
    final ProvidedContent content = getProvidedContent();
    InputStream in = content.getInputStream();
    try {
      return FileUtil.loadBytes(in, content.getLength());
    }
    finally {
      in.close();
    }
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

  void refresh(final boolean asynchronous, final boolean recursive, final boolean noFileWatcher, final Runnable postRunnable) {
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
              VirtualFileImpl parent = getParent();
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
          ourFileSystem.refresh(VirtualFileImpl.this, recursive, true, modalityState, asynchronous, false, noFileWatcher);
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

          ApplicationManager.getApplication().runReadAction(runnable);

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

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    refresh(asynchronous, recursive, false, postRunnable);
  }

  public boolean nameEquals(String name) {
    return SystemInfo.isFileSystemCaseSensitive ? getName().equals(name) : getName().equalsIgnoreCase(name);
  }

  public int getPhysicalFileLength() {
    return (int)getPhysicalFile().length();
  }

  void refreshInternal(final boolean recursive,
                       final ModalityState modalityState,
                       final boolean forceRefresh,
                       final boolean asynchronous,
                       final boolean noWatcher) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }

    if (!isValid()) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("refreshInternal recursive = " + recursive + " asynchronous = " + asynchronous + " file = " + myName);
    }

    final PhysicalFile physicalFile = getPhysicalFile();
    PhysicalFile[] childFiles = null;

    final boolean isDirectory;
    if (myChildren == null) {
      isDirectory = physicalFile.isDirectory();
    }
    else {
      childFiles = physicalFile.listFiles();
      isDirectory = childFiles != null;
    }
    if (isDirectory != myDirectoryFlag) {
      ourFileSystem.getManager().addEventToFireByRefresh(
        new Runnable() {
          public void run() {
            if (!isValid()) return;
            VirtualFileImpl parent = getParent();
            if (parent == null) return;

            ourFileSystem.fireBeforeFileDeletion(null, VirtualFileImpl.this);
            parent.removeChild(VirtualFileImpl.this);
            ourFileSystem.fireFileDeleted(null, VirtualFileImpl.this, myName, myDirectoryFlag, parent);
            VirtualFileImpl newChild = new VirtualFileImpl(parent, physicalFile, isDirectory);
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

      final int[] newIndices = new int[myChildren.length];
      for (int i = 0; i < newIndices.length; i++) newIndices[i] = -1;

      VirtualFileImpl[] children = myChildren;
      for (int i = 0; i < childFiles.length; i++) {
        final PhysicalFile file = childFiles[i];
        final String name = file.getName();
        int index = -1;
        if (i < children.length && children[i].nameEquals(name)) {
          index = i;
        }
        else {
          for (int j = 0; j < children.length; j++) {
            VirtualFileImpl child = myChildren[j];
            if (child.nameEquals(name)) index = j;
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
          newIndices[index] = i;
        }
      }
      for (int i = 0; i < children.length; i++) {
        final VirtualFileImpl child = children[i];
        final int newIndex = newIndices[i];
        if (newIndex >= 0) {
          final String oldName = child.getName();
          final String newName = childFiles[newIndex].getName();
          if (!oldName.equals(newName)) {
            ourFileSystem.getManager().addEventToFireByRefresh(
                      new Runnable() {
                        public void run() {
                          if (child.isValid()) {
                            ourFileSystem.fireBeforePropertyChange(null, child, VirtualFile.PROP_NAME, oldName, newName);
                            child.setName(newName);
                            ourFileSystem.firePropertyChanged(null, child, VirtualFile.PROP_NAME, oldName, newName);
                          }
                        }
                      },
                      asynchronous,
                      modalityState
            );
          }
          if (recursive) {
            ourFileSystem.refreshInner(child, true, modalityState, asynchronous, false, noWatcher);
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
                if (!forceRefresh && (timeStamp == myTimeStamp || !isValid())) return;

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
      ourFileSystem.myUnaccountedFiles.remove(child.getPath());
    }
  }

  void removeChild(VirtualFileImpl child) {
    if (ourFileSystem.myUnaccountedFiles.containsValue(child)) {
      //this file is not in its parent myChildren
      ourFileSystem.myUnaccountedFiles.put(child.getPath(), null);
      child.myParent = null;
      return;
    }

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

  @NonNls
  public String toString() {
    return "VirtualFile: " + getPresentableUrl();
  }

  void setName(String name) {
    myName = new String(name);
  }
}
