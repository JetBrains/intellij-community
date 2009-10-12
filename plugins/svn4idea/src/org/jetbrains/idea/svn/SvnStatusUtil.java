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
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.util.Arrays;
import java.util.List;

public class SvnStatusUtil {
  private final static List<SVNStatusType> ourUnderAndLive = Arrays.asList(SVNStatusType.STATUS_ADDED, SVNStatusType.STATUS_CONFLICTED,
    SVNStatusType.STATUS_INCOMPLETE, SVNStatusType.MERGED, SVNStatusType.STATUS_MODIFIED, SVNStatusType.STATUS_EXTERNAL,
    SVNStatusType.STATUS_NORMAL, SVNStatusType.STATUS_REPLACED);

  private final static List<SVNStatusType> ourCanBeAdded = Arrays.asList(SVNStatusType.STATUS_IGNORED, SVNStatusType.STATUS_NONE,
                                                                         SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.UNKNOWN);

  private SvnStatusUtil() {
  }

  public static boolean notUnderControl(final SVNStatus status) {
    return (status == null) || (ourCanBeAdded.contains(status.getContentsStatus()));
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

  public static boolean isValidUnderControlParent(final SVNStatus status) {
    if (status == null) {
      return false;
    }
    if (status.isSwitched() || status.isCopied()) {
      return true;
    }
    return ourUnderAndLive.contains(status.getContentsStatus());
  }

  public static boolean fileCanBeAdded(final Project project, final VirtualFile file) {
    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    return isIgnoredInAnySense(clManager, file) || clManager.isUnversioned(file);
  }
}
