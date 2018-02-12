/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.PropertiesComponent;

import java.io.File;

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
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) {
    File[] ioFiles = toIoFiles(files);
    ToolWindow w = ToolWindowManager.getInstance(vcs.getProject()).getToolWindow(PropertiesComponent.ID);
    PropertiesComponent component;
    if (w == null) {
      component = new PropertiesComponent();
      w = ToolWindowManager.getInstance(vcs.getProject()).registerToolWindow(PropertiesComponent.ID, component, ToolWindowAnchor.BOTTOM);
    }
    else {
      component = ((PropertiesComponent)w.getContentManager().getContents()[0].getComponent());
    }
    w.setTitle(ioFiles[0].getName());
    w.show(null);
    w.activate(() -> component.setFile(vcs, ioFiles[0]));
  }

  protected boolean isBatchAction() {
    return false;
  }
}
