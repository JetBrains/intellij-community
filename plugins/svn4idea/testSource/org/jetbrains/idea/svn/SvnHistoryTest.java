/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession;
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/27/12
 * Time: 5:15 PM
 */
public class SvnHistoryTest extends Svn17TestCase {
  private volatile int myCnt;

  @Test
  public void testRepositoryRootHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl, true);
    provider.reportAppendableHistory(rootPath, new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
        semaphore.up();
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertTrue(myCnt > 0);
  }

  @Test
  public void testSimpleHistory() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myCnt = 0;
    final VcsHistoryProvider provider = SvnVcs.getInstance(myProject).getVcsHistoryProvider();
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    for (int i = 0; i < 10; i++) {
      editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n" + i);
      checkin();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final FilePath rootPath = VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(myRepoUrl + "/root/source/s1.txt", true);
    provider.reportAppendableHistory(rootPath, new VcsAppendableHistorySessionPartner() {
      @Override
      public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
      }

      @Override
      public void acceptRevision(VcsFileRevision revision) {
        ++ myCnt;
      }

      @Override
      public void reportException(VcsException exception) {
        throw new RuntimeException(exception);
      }

      @Override
      public void finished() {
        semaphore.up();
      }

      @Override
      public void forceRefresh() {
      }
    });
    semaphore.waitFor(1000);

    Assert.assertEquals(11, myCnt);
  }
}
