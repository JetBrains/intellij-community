// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.dialogs.SetPropertyDialog;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;

import static com.intellij.util.containers.ContainerUtil.ar;
import static org.jetbrains.idea.svn.SvnUtil.toIoFiles;

public class SetPropertyAction extends BasicAction {
  @Override
  protected @NotNull String getActionName() {
    return SvnBundle.message("action.name.set.property");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(vcs.getProject()).getStatus(file);

    return !FileStatus.IGNORED.equals(status) && !FileStatus.UNKNOWN.equals(status);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) throws VcsException {
    File[] ioFiles = toIoFiles(files);
    SetPropertyDialog dialog = new SetPropertyDialog(vcs.getProject(), ioFiles, null, true);

    if (dialog.showAndGet()) {
      String name = dialog.getPropertyName();
      String value = dialog.getPropertyValue();
      boolean recursive = dialog.isRecursive();

      for (File ioFile : ioFiles) {
        PropertyClient client = vcs.getFactory(ioFile).createPropertyClient();

        // TODO: most likely SVNDepth.getInfinityOrEmptyDepth should be used instead of SVNDepth.fromRecursive - to have either "infinity"
        // TODO: or "empty" depth, and not "infinity" or "files" depth. But previous logic used SVNDepth.fromRecursive implicitly
        client.setProperty(ioFile, name, PropertyValue.create(value), Depth.allOrFiles(recursive), false);
      }
      for (VirtualFile file : files) {
        if (recursive && file.isDirectory()) {
          VcsDirtyScopeManager.getInstance(vcs.getProject()).dirDirtyRecursively(file);
        }
        else {
          VcsDirtyScopeManager.getInstance(vcs.getProject()).fileDirty(file);
        }
      }
    }
  }

  @Override
  protected boolean isBatchAction() {
    return true;
  }
}
