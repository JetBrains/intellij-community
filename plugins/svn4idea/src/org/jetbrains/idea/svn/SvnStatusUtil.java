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
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class SvnStatusUtil {
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
