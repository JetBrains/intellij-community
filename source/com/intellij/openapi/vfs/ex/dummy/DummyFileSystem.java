
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

/**
 *
 */
public class DummyFileSystem extends VirtualFileSystem implements ApplicationComponent {
  @NonNls public static final String PROTOCOL = "dummy";

  public static DummyFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(DummyFileSystem.class);
  }

  public DummyFileSystem() {
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public VirtualFile createRoot(String name) {
    final VirtualFileDirectoryImpl root = new VirtualFileDirectoryImpl(this, null, name);
    fireFileCreated(null, root);
    return root;
  }

  protected void fireFileCreated(Object requestor, VirtualFile file) {
    super.fireFileCreated(requestor, file);
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, isDirectory, parent);
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    super.fireBeforeFileDeletion(requestor, file);
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public VirtualFile findFileByPath(String path) {
//    LOG.error("method not implemented");
    return null;
  }

  public String extractPresentableUrl(String path) {
    return path;
  }

  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public String getComponentName() {
    return "DummyFileSystem";
  }

  public void forceRefreshFile(VirtualFile file) {
  }
}
