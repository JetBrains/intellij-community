// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.history.VcsHistorySessionConsumer;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static com.intellij.vcsUtil.VcsUtil.getFilePathOnNonLocal;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SvnHistoryTest extends SvnTestCase {
  private SubTree tree;

  @Override
  public void before() throws Exception {
    super.before();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      editFileInCommand(tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }
  }

  @Test
  public void testRepositoryRootHistory() throws Exception {
    int count = reportHistory(getFilePathOnNonLocal(myRepoUrl, true), true);
    assertTrue(count > 0);
  }

  @Test
  public void testSimpleHistory() throws Exception {
    int count = reportHistory(getFilePathOnNonLocal(myRepoUrl + "/root/source/s1.txt", true), false);
    assertEquals(11, count);
  }

  @Test
  public void testSimpleHistoryLocal() throws Exception {
    int count = reportHistory(tree.myS1File);
    assertEquals(11, count);
  }

  @Test
  public void testLocallyRenamedFileHistory() throws Exception {
    renameFileInCommand(tree.myS1File, "renamed.txt");
    refreshChanges();

    int count = reportHistory(tree.myS1File);
    assertEquals(11, count);
  }

  @Test
  public void testLocallyMovedToRenamedDirectory() throws Exception {
    renameFileInCommand(tree.myTargetDir, "renamedTarget");
    moveFileInCommand(tree.myS1File, tree.myTargetDir);
    refreshChanges();

    int count = reportHistory(tree.myS1File);
    assertEquals(11, count);

    count = reportHistory(tree.myTargetDir);
    assertEquals(1, count);

    count = reportHistory(tree.myTargetFiles.get(0));
    assertEquals(1, count);
  }

  private int reportHistory(@NotNull VirtualFile file) throws VcsException {
    return reportHistory(getFilePath(file), false);
  }

  private int reportHistory(@NotNull FilePath path, boolean firstOnly) throws VcsException {
    Semaphore semaphore = new Semaphore();
    AtomicInteger count = new AtomicInteger();

    semaphore.down();
    vcs.getVcsHistoryProvider().reportAppendableHistory(path, new VcsHistorySessionConsumer() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        count.incrementAndGet();
        if (firstOnly) {
          semaphore.up();
        }
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        if (!firstOnly) {
          semaphore.up();
        }
      }
    });

    semaphore.waitFor(1000);

    return count.get();
  }
}
