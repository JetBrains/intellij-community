package com.intellij.testFramework;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import junit.framework.Assert;

import java.io.*;

public class MockVirtualFile extends VirtualFile {
  protected String myContent = "";
  protected String myName = "";
  protected long myModStamp = LocalTimeCounter.currentTime();
  protected long myTimeStamp = System.currentTimeMillis();
  protected long myActualTimeStamp = myTimeStamp;
  private boolean myIsWritable = true;
  private VirtualFileListener myListener = null;

  public MockVirtualFile() {
  }

  public MockVirtualFile(String name) {
    myName = name;
  }

  public MockVirtualFile(String name, String content) {
    myName = name;
    myContent = content;
  }

  public void setListener(VirtualFileListener listener) {
    myListener = listener;
  }

  public VirtualFileSystem getFileSystem() {
    return null;
  }

  public String getPath() {
    return "/" + getName();
  }

  public String getName() {
    return myName;
  }

  public void rename(Object requestor, String newName) throws IOException {
  }

  public boolean isWritable() {
    return myIsWritable;
  }

  public boolean isDirectory() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return null;
  }

  public VirtualFile[] getChildren() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    return null;
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    return null;
  }

  public void delete(Object requestor) throws IOException {
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
  }

  public InputStream getInputStream() throws IOException {
    return null;
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    return null;
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContent.getBytes();
  }

  public char[] contentsToCharArray() throws IOException {
    return myContent.toCharArray();
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public long getTimeStamp() {
    return myTimeStamp;
  }

  public long getActualTimeStamp() {
    return myActualTimeStamp;
  }

  public void setActualTimeStamp(long actualTimeStamp) {
    myActualTimeStamp = actualTimeStamp;
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    }
    catch (IOException e) {
      e.printStackTrace();
      Assert.assertTrue(false);
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  public Reader getReader() throws IOException {
    return new CharArrayReader(contentsToCharArray());
  }

  public Writer getWriter(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return new CharArrayWriter() {
      public void close() {
        super.close();
        myModStamp = newModificationStamp;
        myContent = toString();
      }
    };
  }

  public void setContent(Object requestor, String content, boolean fireEvent) {
    long oldStamp = myModStamp;
    myContent = content;
    if (fireEvent) {
      myModStamp = LocalTimeCounter.currentTime();
      myListener.contentsChanged(new VirtualFileEvent(requestor, this, null, oldStamp, myModStamp));
    }
  }

  public VirtualFile self() {
    return this;
  }

  public void setWritable(boolean b) {
    myIsWritable = b;
  }
}
