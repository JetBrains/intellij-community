package org.jetbrains.idea.maven.vfs;

import com.intellij.openapi.vfs.DeprecatedVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class MavenPropertiesVirtualFile extends DeprecatedVirtualFile {
  private final String myName;
  private final VirtualFileSystem myFS;
  private volatile byte[] myContent = new byte[0];

  public MavenPropertiesVirtualFile(String name, VirtualFileSystem FS) {
    myName = name;
    myFS = FS;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFS;
  }

  public String getPath() {
    return myName;
  }

  public boolean isWritable() {
    return false;
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
    return null;
  }

  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        myContent = toByteArray();
      }
    };
  }

  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myContent;
  }

  public long getTimeStamp() {
    return -1;
  }

  @Override
  public long getModificationStamp() {
    return myContent.hashCode();
  }

  public long getLength() {
    return myContent.length;
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(myContent);
  }
}
