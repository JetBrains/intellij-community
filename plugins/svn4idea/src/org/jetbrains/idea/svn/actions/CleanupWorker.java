// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnProgressCanceller;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.vcsUtil.VcsFileUtil.markFilesDirty;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class CleanupWorker extends Task.Backgroundable {

  @NotNull protected final List<VirtualFile> myRoots;
  @NotNull private final SvnVcs myVcs;
  @NotNull private final List<Pair<VcsException, VirtualFile>> myExceptions = newArrayList();

  public CleanupWorker(@NotNull SvnVcs vcs, @NotNull List<? extends VirtualFile> roots) {
    this(vcs, roots, null);
  }

  public CleanupWorker(@NotNull SvnVcs vcs, @NotNull List<? extends VirtualFile> roots, @Nullable String title) {
    super(vcs.getProject(), notNull(title, message("action.Subversion.cleanup.progress.title")));
    myVcs = vcs;
    myRoots = newArrayList(roots);
  }

  public void execute() {
    StoreUtil.saveDocumentsAndProjectSettings(myVcs.getProject());

    fillRoots();
    if (!myRoots.isEmpty()) {
      queue();
    }
  }

  protected void fillRoots() {
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    for (VirtualFile root : myRoots) {
      try {
        File path = virtualToIoFile(root);
        File pathOrParent = virtualToIoFile(root.isDirectory() ? root : root.getParent());

        indicator.setText(message("action.Subversion.cleanup.progress.text", path));
        myVcs.getFactory(path).createCleanupClient().cleanup(pathOrParent, new SvnProgressCanceller(indicator));
      }
      catch (VcsException e) {
        myExceptions.add(Pair.create(e, root));
      }
    }
  }

  @Override
  public void onCancel() {
    onSuccess();
  }

  @Override
  public void onSuccess() {
    if (myProject.isDisposed()) return;

    getApplication().invokeLater(() -> getApplication().runWriteAction(() -> {
      if (!myProject.isDisposed()) {
        LocalFileSystem.getInstance().refreshFiles(myRoots, false, true, null);
      }
    }));
    markFilesDirty(myProject, myRoots);

    if (!myExceptions.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showErrors(
        myExceptions.stream()
          .map(pair -> new VcsException(
            message("action.Subversion.cleanup.error.message", toSystemDependentName(pair.second.getPath()),
                    pair.first == null ? "" : pair.first.getMessage())))
          .collect(toList()),
        myTitle);
    }
  }
}
