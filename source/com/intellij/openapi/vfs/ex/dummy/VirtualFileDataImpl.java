
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LocalTimeCounter;

import java.io.*;

/**
 *
 */
class VirtualFileDataImpl extends VirtualFileImpl {
  private byte[] myContents = new byte[0];
  private long myModificationStamp = LocalTimeCounter.currentTime();

  public VirtualFileDataImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  public boolean isDirectory() {
    return false;
  }

  public long getLength() {
    return myContents.length;
  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    throw new IOException();
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    throw new IOException();
  }

  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(myContents);
  }

  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
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
        myContents = out.toByteArray();
        myModificationStamp = newModificationStamp >= 0 ? newModificationStamp : LocalTimeCounter.currentTime();
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContents;
  }

  public char[] contentsToCharArray() throws IOException {
    Reader reader = getReader();
    char[] chars = new char[myContents.length];
    int count = reader.read(chars, 0, chars.length);
    reader.close();
    if (count == chars.length){
      return chars;
    }
    else{
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp, Object requestor) {
    myModificationStamp = modificationStamp;
  }
}
