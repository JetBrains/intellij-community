// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;


public class SvnRollbackEnvironment extends DefaultRollbackEnvironment {
  private final SvnVcs mySvnVcs;

  public SvnRollbackEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public String getRollbackOperationName() {
    return message("action.name.revert");
  }

  @Override
  public void rollbackChanges(List<? extends Change> changes,
                              List<VcsException> exceptions,
                              @NotNull RollbackProgressListener listener) {
    listener.indeterminate();

    for (Map.Entry<Pair<Url, WorkingCopyFormat>, Collection<Change>> entry : SvnUtil.splitChangesIntoWc(mySvnVcs, changes).entrySet()) {
      // to be more sure about nested changes, being or being not reverted
      List<Change> sortedChanges = ContainerUtil.sorted(entry.getValue(), ChangesAfterPathComparator.getInstance());

      rollbackGroupForWc(sortedChanges, exceptions, listener);
    }
  }

  private void rollbackGroupForWc(@NotNull List<? extends Change> changes,
                                  @NotNull List<? super VcsException> exceptions,
                                  @NotNull RollbackProgressListener listener) {
    final UnversionedAndNotTouchedFilesGroupCollector collector = new UnversionedAndNotTouchedFilesGroupCollector();
    final ChangesChecker checker = new ChangesChecker(mySvnVcs, collector);

    checker.gather(changes);
    exceptions.addAll(checker.getExceptions());

    final Reverter reverter = new Reverter(mySvnVcs, listener, exceptions);
    reverter.moveRenamesToTmp(collector);
    reverter.revert(checker.getForAdds(), true);
    reverter.revert(checker.getForDeletes(), true);
    reverter.revert(checker.getForEdits(), false);
    reverter.moveGroup();

    for (Couple<File> pair : collector.getToBeDeleted()) {
      if (pair.getFirst().exists()) {
        FileUtil.delete(pair.getSecond());
      }
    }
  }

  @Override
  public void rollbackMissingFileDeletion(List<? extends FilePath> filePaths,
                                          List<? super VcsException> exceptions,
                                          @NotNull RollbackProgressListener listener) {
    for (FilePath filePath : filePaths) {
      listener.accept(filePath);
      try {
        revertFileOrDir(filePath);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
  }

  private void revertFileOrDir(@NotNull FilePath filePath) throws VcsException {
    File file = filePath.getIOFile();
    Info info = mySvnVcs.getInfo(file);
    if (info != null) {
      if (info.isFile()) {
        doRevert(file, false);
      }
      else if (Info.SCHEDULE_ADD.equals(info.getSchedule()) ||
               mySvnVcs.getWorkingCopyFormat(file).isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN)) {
        doRevert(file, true);
      }
      else {
        // do update to restore missing directory.
        mySvnVcs.getFactory(file).createUpdateClient().doUpdate(file, Revision.HEAD, Depth.INFINITY, false, false);
      }
    }
    else {
      throw new VcsException(message("error.could.not.get.info.for.path", file.getPath()));
    }
  }

  private void doRevert(@NotNull File path, boolean recursive) throws VcsException {
    mySvnVcs.getFactory(path).createRevertClient().revert(Collections.singletonList(path), Depth.allOrFiles(recursive), null);
  }

  public static boolean isMoveRenameReplace(@NotNull Change c) {
    if (c.getAfterRevision() == null || c.getBeforeRevision() == null) return false;

    return c.isIsReplaced() ||
           c.isMoved() ||
           c.isRenamed() ||
           (!Comparing.equal(c.getBeforeRevision().getFile(), c.getAfterRevision().getFile()));
  }
}
