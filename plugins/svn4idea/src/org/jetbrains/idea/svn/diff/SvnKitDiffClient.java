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

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.svnkit.SvnKitProgressCanceller;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNReporter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitDiffClient extends BaseSvnClient implements DiffClient {

  @NotNull
  @Override
  public List<Change> compare(@NotNull SvnTarget target1, @NotNull SvnTarget target2) throws VcsException {
    DiffExecutor executor = new DiffExecutor(target1, target2);

    try {
      executor.run();
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }

    return executor.getChanges();
  }

  @Override
  public void unifiedDiff(@NotNull SvnTarget target1, @NotNull SvnTarget target2, @NotNull OutputStream output) throws VcsException {
    assertUrl(target1);
    assertUrl(target2);

    try {
      myVcs.getSvnKitManager().createDiffClient()
        .doDiff(target1.getURL(), target1.getPegRevision(), target2.getURL(), target2.getPegRevision(), true, false, output);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  private class DiffExecutor {
    @NotNull private final SvnTarget myTarget1;
    @NotNull private final SvnTarget myTarget2;
    @NotNull private final List<Change> myChanges;

    private DiffExecutor(@NotNull SvnTarget target1, @NotNull SvnTarget target2) {
      this.myTarget1 = target1;
      this.myTarget2 = target2;
      myChanges = ContainerUtil.newArrayList();
    }

    @NotNull
    public List<Change> getChanges() {
      return myChanges;
    }

    public void run() throws SVNException {
      assertUrl(myTarget1);

      if (myTarget2.isFile()) {
        assertDirectory(myTarget2);

        WorkingCopyFormat format = myVcs.getWorkingCopyFormat(myTarget2.getFile());
        myChanges.addAll(WorkingCopyFormat.ONE_DOT_SIX.equals(format) ? run16Diff() : run17Diff());
      }
      else {
        myChanges.addAll(runUrlDiff());
      }
    }

    private Collection<Change> runUrlDiff() throws SVNException {
      SVNRepository sourceRepository = myVcs.getSvnKitManager().createRepository(myTarget1.getURL());
      sourceRepository.setCanceller(new SvnKitProgressCanceller());
      SvnDiffEditor diffEditor;
      final long rev;
      SVNRepository targetRepository = null;
      try {
        rev = sourceRepository.getLatestRevision();
        // generate Map of path->Change
        targetRepository = myVcs.getSvnKitManager().createRepository(myTarget2.getURL());
        diffEditor = new SvnDiffEditor(sourceRepository, targetRepository, -1, false);
        final ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(diffEditor, new SvnKitProgressCanceller(), null);
        sourceRepository.diff(myTarget2.getURL(), rev, rev, null, true, true, false, reporter -> {
          reporter.setPath("", null, rev, false);
          reporter.finishReport();
        }, cancellableEditor);

        return diffEditor.getChangesMap().values();
      }
      finally {
        sourceRepository.closeSession();
        if (targetRepository != null) {
          targetRepository.closeSession();
        }
      }
    }

    private Collection<Change> run17Diff() throws SVNException {
      final Info info1 = myVcs.getInfo(myTarget2.getFile(), SVNRevision.HEAD);

      if (info1 == null) {
        SVNErrorMessage err =
          SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", myTarget2);
        SVNErrorManager.error(err, SVNLogType.WC);
      }
      else if (info1.getURL() == null) {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", myTarget2);
        SVNErrorManager.error(err, SVNLogType.WC);
      }

      final SVNReporter17 reporter17 =
        new SVNReporter17(myTarget2.getFile(), new SVNWCContext(myVcs.getSvnKitManager().getSvnOptions(), new ISVNEventHandler() {
          @Override
          public void handleEvent(SVNEvent event, double progress) {
          }

          @Override
          public void checkCancelled() {
          }
        }), false, true, SVNDepth.INFINITY, false, false, true, false, SVNDebugLog.getDefaultLog());
      SVNRepository repository = null;
      SVNRepository repository2 = null;
      try {
        repository = myVcs.getSvnKitManager().createRepository(info1.getURL());
        long rev = repository.getLatestRevision();
        repository2 = myVcs.getSvnKitManager().createRepository(myTarget1.getURL());
        SvnDiffEditor diffEditor = new SvnDiffEditor(myTarget2.getFile(), repository2, rev, true);
        repository.diff(myTarget1.getURL(), rev, rev, null, true, SVNDepth.INFINITY, false, reporter17,
                        SVNCancellableEditor.newInstance(diffEditor, new SvnKitProgressCanceller(), null));

        return diffEditor.getChangesMap().values();
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

    private Collection<Change> run16Diff() throws SVNException {
      // here there's 1.6 copy so ok to use SVNWCAccess
      final SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      wcAccess.setOptions(myVcs.getSvnKitManager().getSvnOptions());
      SVNRepository repository = null;
      SVNRepository repository2 = null;
      try {
        SVNAdminAreaInfo info = wcAccess.openAnchor(myTarget2.getFile(), false, SVNWCAccess.INFINITE_DEPTH);
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

        repository = myVcs.getSvnKitManager().createRepository(anchorURL.toString());
        long rev = repository.getLatestRevision();
        repository2 =
          myVcs.getSvnKitManager().createRepository((target == null) ? myTarget1.getURL() : myTarget1.getURL().removePathTail());
        SvnDiffEditor diffEditor =
          new SvnDiffEditor(target == null ? myTarget2.getFile() : myTarget2.getFile().getParentFile(), repository2, rev, true);
        repository.diff(myTarget1.getURL(), rev, rev, target, true, true, false, reporter,
                        SVNCancellableEditor.newInstance(diffEditor, new SvnKitProgressCanceller(), null));

        return diffEditor.getChangesMap().values();
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
  }
}
