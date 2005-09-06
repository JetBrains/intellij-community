
package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;

public class HttpFileSystem extends VirtualFileSystem implements ApplicationComponent {

  public static final String PROTOCOL = "http";

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

  public void forceRefreshFile(VirtualFile file) {
  }
}