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

import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.options.Configurable;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.jetbrains.idea.svn.status.SvnStatusEnvironment;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.io.File;

public abstract class AbstractSvnUpdateIntegrateEnvironment implements UpdateEnvironment {
  protected final SvnVcs myVcs;

  protected AbstractSvnUpdateIntegrateEnvironment(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  public UpdateSession updateDirectories(FilePath[] contentRoots,
                                         final UpdatedFiles updatedFiles,
                                         final ProgressIndicator progressIndicator)
    throws ProcessCanceledException {

    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    ISVNEventHandler eventHandler = new UpdateEventHandler(myVcs, progressIndicator, updatedFiles);

    boolean totalUpdate = true;
    AbstractUpdateIntegrateCrawler crawler = createCrawler(eventHandler, totalUpdate, exceptions, updatedFiles);

    Collection<File> updatedRoots = new HashSet<File>();
    for (FilePath contentRoot : contentRoots) {
      if (progressIndicator != null && progressIndicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (contentRoot.getIOFile() != null) {
        Collection<File> roots = SvnUtil.crawlWCRoots(contentRoot.getIOFile(), crawler, progressIndicator);
        updatedRoots.addAll(roots);

      }
    }
    if (updatedRoots.isEmpty()) {
      Messages.showErrorDialog(myVcs.getProject(), SvnBundle.message("message.text.update.no.directories.found"), SvnBundle.message("messate.text.update.error"));
    }

    final Collection<String> conflictedFiles = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).getFiles();
    return new UpdateSessionAdapter(exceptions, false) {
      public void onRefreshFilesCompleted() {
        if (conflictedFiles != null && !conflictedFiles.isEmpty()) {
          List<VirtualFile> vfFiles = new ArrayList<VirtualFile>();
          for (final String conflictedFile : conflictedFiles) {
            @NonNls String path = conflictedFile;
            path = "file://" + path.replace(File.separatorChar, '/');
            VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(path);
            if (vf != null) {
              vfFiles.add(vf);
            }
          }
          if (!vfFiles.isEmpty()) {
            AbstractVcsHelper.getInstance(myVcs.getProject()).showMergeDialog(vfFiles,
                                                                              new SvnMergeProvider(myVcs.getProject()),
                                                                              null);
          }
        }
      }

    };
  }

  protected abstract AbstractUpdateIntegrateCrawler createCrawler(ISVNEventHandler eventHandler,
                                                 boolean totalUpdate,
                                                 ArrayList<VcsException> exceptions, UpdatedFiles updatedFiles);

  public abstract Configurable createConfigurable(Collection<FilePath> collection);

  private static class UpdateEventHandler implements ISVNEventHandler {
    private final ProgressIndicator myProgressIndicator;
    private final UpdatedFiles myUpdatedFiles;
    private int myExternalsCount;
    private SvnVcs myVCS;
    @NonNls public static final String SKIP_ID = "skip";

    public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
      myProgressIndicator = progressIndicator;
      myUpdatedFiles = updatedFiles;
      myVCS = vcs;
      myExternalsCount = 1;
    }

    public void handleEvent(SVNEvent event, double progress) {
      if (event == null || event.getFile() == null) {
        return;
      }
      String path = event.getFile().getAbsolutePath();
      String displayPath = event.getFile().getName();
      if (event.getAction() == SVNEventAction.UPDATE_ADD ||
          event.getAction() == SVNEventAction.ADD) {
        myProgressIndicator.setText2(SvnBundle.message("progress.text2.added", displayPath));
        myUpdatedFiles.getGroupById(FileGroup.CREATED_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
        myProgressIndicator.setText2(SvnBundle.message("progress.text2.deleted", displayPath));
        myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
          myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(path);
          myProgressIndicator.setText2(SvnBundle.message("progress.text2.conflicted", displayPath));
        }
        else if (event.getContentsStatus() == SVNStatusType.MERGED || event.getPropertiesStatus() == SVNStatusType.MERGED) {
          myProgressIndicator.setText2(SvnBundle.message("progres.text2.merged", displayPath));
          myUpdatedFiles.getGroupById(FileGroup.MERGED_ID).add(path);
        }
        else if (event.getContentsStatus() == SVNStatusType.CHANGED || event.getPropertiesStatus() == SVNStatusType.CHANGED) {
          myProgressIndicator.setText2(SvnBundle.message("progres.text2.updated", displayPath));
          myUpdatedFiles.getGroupById(FileGroup.UPDATED_ID).add(path);
        }
        else if (event.getContentsStatus() == SVNStatusType.UNCHANGED && event.getPropertiesStatus() == SVNStatusType.UNCHANGED) {
          myProgressIndicator.setText2(SvnBundle.message("progres.text2.updated", displayPath));
        }
        else {
          myProgressIndicator.setText2("");
          myUpdatedFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path);
        }
      }
      else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
        myExternalsCount++;
        if (myUpdatedFiles.getGroupById(SvnStatusEnvironment.EXTERNAL_ID) == null) {
          myUpdatedFiles.registerGroup(new FileGroup(SvnBundle.message("status.group.name.externals"),
                                                     SvnBundle.message("status.group.name.externals"),
                                                     false, SvnStatusEnvironment.EXTERNAL_ID, true));
        }
        myUpdatedFiles.getGroupById(SvnStatusEnvironment.EXTERNAL_ID).add(path);
        myProgressIndicator.setText(SvnBundle.message("progress.text.updating.external.location", event.getFile().getAbsolutePath()));
      }
      else if (event.getAction() == SVNEventAction.RESTORE) {
        myProgressIndicator.setText2(SvnBundle.message("progress.text2.restored.file", displayPath));
        myUpdatedFiles.getGroupById(FileGroup.RESTORED_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED && event.getRevision() >= 0) {
        myExternalsCount--;
        myProgressIndicator.setText2(SvnBundle.message("progres.text2.updated.to.revision", event.getRevision()));
        if (myExternalsCount == 0) {
          myExternalsCount = 1;
          WindowManager.getInstance().getStatusBar(myVCS.getProject()).setInfo(
            SvnBundle.message("status.text.updated.to.revision", event.getRevision()));
        }
      }
      else if (event.getAction() == SVNEventAction.SKIP) {
        myProgressIndicator.setText2(SvnBundle.message("progress.text2.skipped.file", displayPath));
        if (myUpdatedFiles.getGroupById(SKIP_ID) == null) {
          myUpdatedFiles.registerGroup(new FileGroup(SvnBundle.message("update.group.name.skipped"),
                                                     SvnBundle.message("update.group.name.skipped"), false, SKIP_ID, true));
        }
        myUpdatedFiles.getGroupById(SKIP_ID).add(path);
      }
    }

    public void checkCancelled() throws SVNCancelException {
      myProgressIndicator.checkCanceled();
      if (myProgressIndicator.isCanceled()) {
        SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"));
      }
    }
  }


}
