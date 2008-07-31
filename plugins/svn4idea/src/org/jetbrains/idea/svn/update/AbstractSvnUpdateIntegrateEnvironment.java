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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;
import java.util.*;

public abstract class AbstractSvnUpdateIntegrateEnvironment implements UpdateEnvironment {
  protected final SvnVcs myVcs;
  private final ProjectLevelVcsManager myVcsManager;
  @NonNls public static final String REPLACED_ID = "replaced";
  @NonNls public static final String EXTERNAL_ID = "external";

  protected AbstractSvnUpdateIntegrateEnvironment(final SvnVcs vcs) {
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(vcs.getProject());
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  @NotNull
  public UpdateSession updateDirectories(@NotNull final FilePath[] contentRoots,
                                         final UpdatedFiles updatedFiles,
                                         final ProgressIndicator progressIndicator, @NotNull final Ref<SequentialUpdatesContext> context)
    throws ProcessCanceledException {

    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    UpdateEventHandler eventHandler = new UpdateEventHandler(myVcs, progressIndicator);
    eventHandler.setUpdatedFiles(updatedFiles);

    boolean totalUpdate = true;
    AbstractUpdateIntegrateCrawler crawler = createCrawler(eventHandler, totalUpdate, exceptions, updatedFiles);

    Collection<File> updatedRoots = new HashSet<File>();
    for (FilePath contentRoot : contentRoots) {
      if (progressIndicator != null && progressIndicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      Collection<File> roots = SvnUtil.crawlWCRoots(contentRoot.getIOFile(), crawler, progressIndicator);
      updatedRoots.addAll(roots);
    }
    if (updatedRoots.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(myVcs.getProject(), SvnBundle.message("message.text.update.no.directories.found"),
                                   SvnBundle.message("messate.text.update.error"));
        }
      });
      return new UpdateSessionAdapter(Collections.<VcsException>emptyList(), true);
    }

    final FileGroup conflictedGroup = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID);
    final Collection<String> conflictedFiles = conflictedGroup.getFiles();
    return new UpdateSessionAdapter(exceptions, false) {
      public void onRefreshFilesCompleted() {
        for(FilePath contentRoot: contentRoots) {
          // update switched/ignored status of directories
          VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(contentRoot);
        }
        if (conflictedFiles != null && !conflictedFiles.isEmpty() && !isDryRun()) {
          List<VirtualFile> vfFiles = new ArrayList<VirtualFile>();
          for (final String conflictedFile : conflictedFiles) {
            @NonNls final String path = "file://" + conflictedFile.replace(File.separatorChar, '/');
            VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
              @Nullable public VirtualFile compute() {
                return VirtualFileManager.getInstance().findFileByUrl(path);
              }

            });
            if (vf != null) {
              // refresh base directory so that conflict files should be detected
              vf.getParent().refresh(true, false);
              VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(vf);
              if (myVcs.equals(ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsFor(vf))) {

                final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(myVcs.getProject()).ensureFilesWritable(vf);
                if (! operationStatus.hasReadonlyFiles()) {
                  vfFiles.add(vf);
                }
              }
            }
          }
          if (!vfFiles.isEmpty()) {
            final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(myVcs.getProject());
            List<VirtualFile> mergedFiles = vcsHelper.showMergeDialog(vfFiles, new SvnMergeProvider(myVcs.getProject()));
            FileGroup mergedGroup = updatedFiles.getGroupById(FileGroup.MERGED_ID);
            for(VirtualFile mergedFile: mergedFiles) {
              String path = FileUtil.toSystemDependentName(mergedFile.getPresentableUrl());
              VcsRevisionNumber revision = conflictedGroup.getRevision(myVcsManager, path);
              conflictedGroup.remove(path);
              if (revision != null) {
                mergedGroup.add(path, myVcs, revision);
              }
              else {
                mergedGroup.add(path);
              }
            }
          }
        }
      }

    };
  }

  protected boolean isDryRun() {
    return false;
  }

  protected abstract AbstractUpdateIntegrateCrawler createCrawler(ISVNEventHandler eventHandler,
                                                 boolean totalUpdate,
                                                 ArrayList<VcsException> exceptions, UpdatedFiles updatedFiles);

  @Nullable
  public abstract Configurable createConfigurable(Collection<FilePath> collection);
}
