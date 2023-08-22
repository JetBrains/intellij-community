// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class SvnStatusUtil {
  private SvnStatusUtil() {
  }

  public static boolean isUnderControl(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return isUnderControl(vcs.getProject(), file);
  }

  public static boolean isUnderControl(@NotNull Project project, @NotNull VirtualFile file) {
    return !isIgnoredInAnySense(project, file) && !ChangeListManager.getInstance(project).isUnversioned(file);
  }

  public static boolean isAdded(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return FileStatus.ADDED.equals(FileStatusManager.getInstance(vcs.getProject()).getStatus(file));
  }

  public static boolean isExplicitlyLocked(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return ChangeListManagerImpl.getInstanceImpl(vcs.getProject()).isLogicallyLocked(file);
  }

  public static boolean isIgnoredInAnySense(@NotNull Project project, @NotNull VirtualFile file) {
    ChangeListManager manager = ChangeListManager.getInstance(project);

    return manager.isIgnoredFile(file) || FileStatus.IGNORED.equals(manager.getStatus(file));
  }
}
