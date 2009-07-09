package org.jetbrains.idea.maven.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MavenPropertiesVirtualFileSystem extends DummyFileSystem {
  @NonNls public static final String PROTOCOL = "maven-properties";

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return new MavenPropertiesVirtualFile(path, this);
  }

  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "foo";
  }

  //protected void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
  //  throw new UnsupportedOperationException();
  //}
  //
  //protected VirtualFile copyFile(Object requestor, VirtualFile virtualFile, VirtualFile newParent, String copyName)
  //  throws IOException {
  //  throw new UnsupportedOperationException();
  //}
}
