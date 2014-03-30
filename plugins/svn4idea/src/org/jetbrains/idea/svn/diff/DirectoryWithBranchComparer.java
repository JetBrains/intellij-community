/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnProgressCanceller;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.status.SvnDiffEditor;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
* @author Konstantin Kolosovsky.
*/
public class DirectoryWithBranchComparer extends ElementWithBranchComparer {

  @NotNull private final StringBuilder titleBuilder = new StringBuilder();
  @NotNull private final List<Change> changes = new ArrayList<Change>();

  public DirectoryWithBranchComparer(@NotNull Project project,
                                     @NotNull VirtualFile virtualFile,
                                     @NotNull String branchUrl,
                                     long branchRevision) {
    super(project, virtualFile, branchUrl, branchRevision);
  }

  @Override
  protected void compare() throws SVNException, VcsException {
    titleBuilder.append(SvnBundle.message("repository.browser.compare.title", myElementUrl,
                                          FileUtil.toSystemDependentName(myVirtualFile.getPresentableUrl())));

    final File ioFile = new File(myVirtualFile.getPath());
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(ioFile);

    if (WorkingCopyFormat.ONE_DOT_EIGHT.equals(format)) {
      // svn 1.7 command line "--summarize" option for "diff" command does not support comparing working copy directories with repository
      // directories - that is why command line is only used explicitly for svn 1.8
      compareWithCommandLine();
    }
    else if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      report17DirDiff();
    }
    else {
      report16DirDiff();
    }
  }

  private void compareWithCommandLine() throws VcsException {
    SvnTarget target1 = SvnTarget.fromFile(new File(myVirtualFile.getPath()));
    SvnTarget target2 = SvnTarget.fromURL(myElementUrl);

    changes.addAll(myVcs.getFactory(target1).createDiffClient().compare(target1, target2));
  }

  private void report17DirDiff() throws SVNException {
    final File ioFile = new File(myVirtualFile.getPath());
    final SVNInfo info1 = myVcs.getInfo(ioFile, SVNRevision.HEAD);

    if (info1 == null) {
      SVNErrorMessage err =
        SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", myVirtualFile.getPath());
      SVNErrorManager.error(err, SVNLogType.WC);
    }
    else if (info1.getURL() == null) {
      SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", myVirtualFile.getPath());
      SVNErrorManager.error(err, SVNLogType.WC);
    }

    final SVNReporter17 reporter17 =
      new SVNReporter17(ioFile, new SVNWCContext(SvnConfiguration.getInstance(myProject).getOptions(myProject), new ISVNEventHandler() {
        @Override
        public void handleEvent(SVNEvent event, double progress) throws SVNException {
        }

        @Override
        public void checkCancelled() throws SVNCancelException {
        }
      }),
                        false, true, SVNDepth.INFINITY, false, false, true, false,
                        SVNDebugLog.getDefaultLog());
    SVNRepository repository = null;
    SVNRepository repository2 = null;
    try {
      repository = myVcs.createRepository(info1.getURL());
      long rev = repository.getLatestRevision();
      repository2 = myVcs.createRepository(myElementUrl.toString());
      SvnDiffEditor diffEditor = new SvnDiffEditor(myVirtualFile, repository2, rev, true);
      repository.diff(myElementUrl, rev, rev, null, true, SVNDepth.INFINITY, false, reporter17,
                      SVNCancellableEditor.newInstance(diffEditor, new SvnProgressCanceller(), null));
      changes.addAll(diffEditor.getChangesMap().values());
    }
    finally {
      if (repository != null) {
        repository.closeSession();
      }
      if (repository2 != null) {
        repository2.closeSession();
      }
    }
  }

  private void report16DirDiff() throws SVNException {
    // here there's 1.6 copy so ok to use SVNWCAccess
    final SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
    wcAccess.setOptions(myVcs.getSvnOptions());
    SVNRepository repository = null;
    SVNRepository repository2 = null;
    try {
      SVNAdminAreaInfo info = wcAccess.openAnchor(new File(myVirtualFile.getPath()), false, SVNWCAccess.INFINITE_DEPTH);
      File anchorPath = info.getAnchor().getRoot();
      String target = "".equals(info.getTargetName()) ? null : info.getTargetName();

      SVNEntry anchorEntry = info.getAnchor().getEntry("", false);
      if (anchorEntry == null) {
        SVNErrorMessage err =
          SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", anchorPath);
        SVNErrorManager.error(err, SVNLogType.WC);
      }
      else if (anchorEntry.getURL() == null) {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", anchorPath);
        SVNErrorManager.error(err, SVNLogType.WC);
      }

      SVNURL anchorURL = anchorEntry.getSVNURL();
      SVNReporter reporter = new SVNReporter(info, info.getAnchor().getFile(info.getTargetName()), false, true, SVNDepth.INFINITY,
                                             false, false, true, SVNDebugLog.getDefaultLog());

      repository = myVcs.createRepository(anchorURL.toString());
      long rev = repository.getLatestRevision();
      repository2 = myVcs.createRepository((target == null) ? myElementUrl.toString() : myElementUrl.removePathTail().toString());
      SvnDiffEditor diffEditor = new SvnDiffEditor((target == null) ? myVirtualFile : myVirtualFile.getParent(),
                                                   repository2, rev, true);
      repository.diff(myElementUrl, rev, rev, target, true, true, false, reporter,
                      SVNCancellableEditor.newInstance(diffEditor, new SvnProgressCanceller(), null));
      changes.addAll(diffEditor.getChangesMap().values());
    }
    finally {
      wcAccess.close();
      if (repository != null) {
        repository.closeSession();
      }
      if (repository2 != null) {
        repository2.closeSession();
      }
    }
  }

  @Override
  protected void onCancel() {
    changes.clear();
  }

  @Override
  protected void showResult() {
    if (!changes.isEmpty()) {
      AbstractVcsHelper.getInstance(myProject).showWhatDiffersBrowser(null, changes, titleBuilder.toString());
    }
  }

  @Override
  public String getTitle() {
    return SvnBundle.message("progress.computing.difference");
  }
}
