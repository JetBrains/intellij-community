
package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class HttpFileSystem extends VirtualFileSystem implements ApplicationComponent {

  @NonNls public static final String PROTOCOL = "http";

  public static HttpFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(HttpFileSystem.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(String path) {
    return new VirtualFileImpl(this, path);
  }

  public String extractPresentableUrl(String path) {
    return VirtualFileManager.constructUrl(PROTOCOL, path);
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public void refresh(boolean asynchronous) {
  }

  public String getComponentName() {
    return "HttpFileSystem";
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull VirtualFile... files) {

  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    throw new UnsupportedOperationException();
  }
}