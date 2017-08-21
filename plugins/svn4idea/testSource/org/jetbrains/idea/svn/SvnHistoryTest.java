/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession;
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SvnHistoryTest extends Svn17TestCase {

  @Test
  public void testRepositoryRootHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl, true);
    int count = reportHistory(provider, rootPath, true);
    Assert.assertTrue(count > 0);
  }

  @Test
  public void testSimpleHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl + "/root/source/s1.txt", true);
    int count = reportHistory(provider, rootPath);
    Assert.assertEquals(11, count);
  }

  @Test
  public void testSimpleHistoryLocal() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    int count = reportHistory(provider, VcsUtil.getFilePath(tree.myS1File));
    Assert.assertEquals(11, count);
  }

  @Test
  public void testLocallyRenamedFileHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    VcsTestUtil.renameFileInCommand(myProject, tree.myS1File, "renamed.txt");
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager.getInstance(myProject).ensureUpToDate(false);

    int count = reportHistory(provider, VcsUtil.getFilePath(tree.myS1File));
    Assert.assertEquals(11, count);
  }

  @Test
  public void testLocallyMovedToRenamedDirectory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    VcsTestUtil.renameFileInCommand(myProject, tree.myTargetDir, "renamedTarget");
    VcsTestUtil.moveFileInCommand(myProject, tree.myS1File, tree.myTargetDir);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager.getInstance(myProject).ensureUpToDate(false);

    int count = reportHistory(provider, VcsUtil.getFilePath(tree.myS1File));
    Assert.assertEquals(11, count);

    count = reportHistory(provider, VcsUtil.getFilePath(tree.myTargetDir));
    Assert.assertEquals(1, count);

    count = reportHistory(provider, VcsUtil.getFilePath(tree.myTargetFiles.get(0)));
    Assert.assertEquals(1, count);
  }

  private static int reportHistory(@NotNull VcsHistoryProvider provider, @NotNull FilePath path) throws VcsException {
    return reportHistory(provider, path, false);
  }

  private static int reportHistory(@NotNull VcsHistoryProvider provider, @NotNull FilePath path, boolean firstOnly) throws VcsException {
    Semaphore semaphore = new Semaphore();
    AtomicInteger count = new AtomicInteger();

    semaphore.down();
    provider.reportAppendableHistory(path, new VcsAppendableHistorySessionPartner() {
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

      @Override
      public void beforeRefresh() {
      }

      @Override
      public void forceRefresh() {
      }
    });

    semaphore.waitFor(1000);

    return count.get();
  }
}
