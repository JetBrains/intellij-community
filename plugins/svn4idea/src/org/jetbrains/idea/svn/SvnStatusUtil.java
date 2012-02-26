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

public class SvnStatusUtil {
  private SvnStatusUtil() {
  }

  public static boolean isUnderControl(final Project project, final VirtualFile file) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    return (! isIgnoredInAnySense(clManager, file)) && (! clManager.isUnversioned(file));
  }

  public static boolean isAdded(final Project project, final VirtualFile file) {
    final FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    return FileStatus.ADDED.equals(status);
  }

  public static boolean isExplicitlyLocked(final Project project, final VirtualFile file) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    return ((ChangeListManagerImpl) clManager).isLogicallyLocked(file);
  }

  public static boolean isIgnoredInAnySense(final ChangeListManager clManager, final VirtualFile file) {
    return clManager.isIgnoredFile(file) || FileStatus.IGNORED.equals(clManager.getStatus(file));
  }

  public static boolean fileCanBeAdded(final Project project, final VirtualFile file) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    return isIgnoredInAnySense(clManager, file) || clManager.isUnversioned(file);
  }
}
