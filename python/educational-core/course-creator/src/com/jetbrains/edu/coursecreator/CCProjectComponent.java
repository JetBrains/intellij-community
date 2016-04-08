package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class CCProjectComponent implements ProjectComponent {
  private final  CCVirtualFileListener myTaskFileLifeListener = new CCVirtualFileListener();

  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "CCProjectComponent";
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myTaskFileLifeListener);
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myTaskFileLifeListener);
  }
}
