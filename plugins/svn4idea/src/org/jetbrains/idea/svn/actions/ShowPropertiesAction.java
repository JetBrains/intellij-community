// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.PropertiesComponent;

import java.io.File;

import static com.intellij.util.ContentsUtil.addContent;
import static com.intellij.util.containers.ContainerUtil.ar;
import static org.jetbrains.idea.svn.SvnUtil.toIoFiles;

public class ShowPropertiesAction extends BasicAction {

  @NotNull
  @Override
  protected String getActionName() {
    return "Show Properties";
  }

  @Override
  protected boolean needsAllFiles() {
    return false;
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(vcs.getProject()).getStatus(file);

    return status != null && !FileStatus.UNKNOWN.equals(status) && !FileStatus.IGNORED.equals(status);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) {
    File[] ioFiles = toIoFiles(files);
    ToolWindow w = ToolWindowManager.getInstance(vcs.getProject()).getToolWindow(PropertiesComponent.ID);
    PropertiesComponent component;
    if (w == null) {
      w = ToolWindowManager.getInstance(vcs.getProject())
                           .registerToolWindow(PropertiesComponent.ID, false, ToolWindowAnchor.BOTTOM, vcs.getProject(), true);
      component = new PropertiesComponent();
      Content content = ContentFactory.SERVICE.getInstance().createContent(component, "", false);
      addContent(w.getContentManager(), content, true);
    }
    else {
      component = (PropertiesComponent)w.getContentManager().getContents()[0].getComponent();
    }
    w.setTitle(ioFiles[0].getName());
    w.show(null);
    w.activate(() -> component.setFile(vcs, ioFiles[0]));
  }

  protected boolean isBatchAction() {
    return false;
  }
}
