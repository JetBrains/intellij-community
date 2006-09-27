/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnWCRootCrawler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

public abstract class AbstractUpdateIntegrateCrawler implements SvnWCRootCrawler {
  protected final SvnVcs myVcs;
  protected final ISVNEventHandler myHandler;
  protected final Collection<VcsException> myExceptions;
  protected final UpdatedFiles myPostUpdateFiles;
  protected final boolean myIsTotalUpdate;

  protected AbstractUpdateIntegrateCrawler(
    final boolean isTotalUpdate,
    final UpdatedFiles postUpdateFiles,
    final Collection<VcsException> exceptions,
    final ISVNEventHandler handler,
    final SvnVcs vcs) {
    myIsTotalUpdate = isTotalUpdate;
    myPostUpdateFiles = postUpdateFiles;
    myExceptions = exceptions;
    myHandler = handler;
    myVcs = vcs;
  }

  public Collection<File> handleWorkingCopyRoot(File root, ProgressIndicator progress) {
    final Collection<File> result = new HashSet<File>();

    long rev;

    if (progress != null) {
      showProgressMessage(progress, root);
    }
    try {
      SVNUpdateClient client = myVcs.createUpdateClient();
      client.setEventHandler(myHandler);

      rev = doUpdate(root, client);

      if (rev < 0) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, SvnBundle.message("exception.text.root.was.not.properly.updated", root)));
      }
    }
    catch (SVNException e) {
      myExceptions.add(new VcsException(e));
    }
    if (!SvnConfiguration.getInstance(myVcs.getProject()).UPDATE_RUN_STATUS) {
      return result;
    }

    try {
      SVNStatusClient statusClient = myVcs.createStatusClient();
      statusClient.setIgnoreExternals(false);

      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.update.computing.post.update.status", root.getAbsolutePath()));
      }
      statusClient.doStatus(root, true, false, false, false, new ISVNStatusHandler() {
        public void handleStatus(SVNStatus status) {
          if (status.getFile() == null) {
            return;
          }
          if (myIsTotalUpdate &&
              status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED &&
              status.getFile().isDirectory()) {
            result.add(status.getFile());
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
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
                // may conflict with update status.
              FileGroup group = myPostUpdateFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID);
              if (group != null && (group.getFiles() == null || !group.getFiles().contains(path))) {
                group.add(path);
              }
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_REMOVED_ID).add(path);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
              myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
                     status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
              myPostUpdateFiles.getGroupById(FileGroup.MODIFIED_ID).add(path);
            }
            else if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                     status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
              if (status.getFile().isFile() || !SVNWCUtil.isVersionedDirectory(status.getFile())) {
                myPostUpdateFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path);
              }
            }
          }
        }
      });
    }
    catch (SVNException e) {
      myExceptions.add(new VcsException(e));
    }
    return result;
  }

  protected abstract void showProgressMessage(ProgressIndicator progress, File root);

  protected abstract long doUpdate(
    File root,
    SVNUpdateClient client) throws
                                                                                                             SVNException;
}
