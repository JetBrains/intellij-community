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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Collection;

public class SvnStatusWorker {
  private final SvnVcs myVcs;
  private final Collection<File> myTouchedFiles;
  private final File myRoot;
  private final boolean myIsTotalUpdate;

  // these two accomulate results
  private final UpdatedFiles myPostUpdateFiles;
  private final Collection<VcsException> myExceptions;

  public SvnStatusWorker(final SvnVcs vcs, final Collection<File> touchedFiles, final File root, final UpdatedFiles postUpdateFiles,
                         final boolean isTotalUpdate,
                         final Collection<VcsException> exceptions) {
    myVcs = vcs;
    myTouchedFiles = touchedFiles;
    myRoot = root;
    myPostUpdateFiles = postUpdateFiles;
    myIsTotalUpdate = isTotalUpdate;
    myExceptions = exceptions;
  }

  public void doStatus() {
    try {
      SVNStatusClient statusClient = myVcs.createStatusClient();
      statusClient.setIgnoreExternals(false);

      final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.update.computing.post.update.status", myRoot.getAbsolutePath()));
      }
      final VcsKey vcsKey = SvnVcs.getKey();
      statusClient.doStatus(myRoot, true, false, false, false, new ISVNStatusHandler() {
        public void handleStatus(SVNStatus status) {
          if (status.getFile() == null) {
            return;
          }
          if (myIsTotalUpdate &&
              status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED &&
              status.getFile().isDirectory()) {
            myTouchedFiles.add(status.getFile());
          }
          if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL ||
              status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
              status.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
              status.getContentsStatus() == SVNStatusType.STATUS_INCOMPLETE) {
            // not interesting in post-update.
          }
          else if (status.getContentsStatus() != SVNStatusType.STATUS_NONE || status.getPropertiesStatus() == SVNStatusType.STATUS_NONE) {
            String path = status.getFile().getAbsolutePath();

            if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path, vcsKey, null);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
                // may conflict with update status.
              FileGroup group = myPostUpdateFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID);
              if (group != null && (group.getFiles() == null || !group.getFiles().contains(path))) {
                group.add(path, vcsKey, null);
              }
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_REMOVED_ID).add(path, vcsKey, null);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path, vcsKey, null);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
                     status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
              myPostUpdateFiles.getGroupById(FileGroup.MODIFIED_ID).add(path, vcsKey, null);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                     status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
              if (status.getFile().isFile() || !SVNWCUtil.isVersionedDirectory(status.getFile())) {
                myPostUpdateFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path, vcsKey, null);
              }
            }
          }
        }
      });
    }
    catch (SVNException e) {
      myExceptions.add(new VcsException(e));
    }
  }
}
