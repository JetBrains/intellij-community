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

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.parseUrl;

public class SvnExternalCommitNoticedTest extends Svn17TestCase {
  private ChangeListManagerImpl clManager;
  private SvnVcs myVcs;
  private VcsDirtyScopeManager myVcsDirtyScopeManager;

  @Override
  @Before
  public void setUp() throws Exception {
    //System.setProperty(FileWatcher.PROPERTY_WATCHER_DISABLED, "false");
    super.setUp();

    clManager = (ChangeListManagerImpl) ChangeListManager.getInstance(myProject);
    myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myVcs = SvnVcs.getInstance(myProject);
  }

  @Test
  public void testSimpleCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "test1");
    VcsTestUtil.editFileInCommand(myProject, tree.myS2File, "test2");
    VcsTestUtil.editFileInCommand(myProject, tree.myTargetFiles.get(1), "target1");

    myVcsDirtyScopeManager.markEverythingDirty();
    clManager.ensureUpToDate(false);
    Assert.assertEquals(3, clManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    checkin();

    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    clManager.ensureUpToDate(false);
    Assert.assertEquals(0, clManager.getChangesIn(myWorkingCopyDir).size());
  }

  @Test
  public void testRenameDirCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    VcsTestUtil.renameFileInCommand(myProject, tree.myTargetDir, "aabbcc");

    myVcsDirtyScopeManager.markEverythingDirty();
    clManager.ensureUpToDate(false);
    Assert.assertEquals(11, clManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    checkin();

    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    clManager.ensureUpToDate(false);
    Assert.assertEquals(0, clManager.getChangesIn(myWorkingCopyDir).size());
  }

  @Test
  public void testExternalSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/source/s1.txt", tree.myS1File.getPath());
    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/target", tree.myTargetDir.getPath());

    sleep(50);
    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    clManager.ensureUpToDate(false);

    Assert.assertEquals(FileStatus.SWITCHED, clManager.getStatus(tree.myS1File));
    Assert.assertEquals(FileStatus.NOT_CHANGED, clManager.getStatus(tree.myS2File));
    Assert.assertEquals(FileStatus.NOT_CHANGED, clManager.getStatus(tree.mySourceDir));
    Assert.assertEquals(FileStatus.SWITCHED, clManager.getStatus(tree.myTargetDir));
    Assert.assertEquals(FileStatus.SWITCHED, clManager.getStatus(tree.myTargetFiles.get(1)));
  }

  @Test
  public void testExternalRootSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    myVcs.invokeRefreshSvnRoots();
    clManager.ensureUpToDate(false);
    clManager.ensureUpToDate(false);
    SvnFileUrlMapping workingCopies = myVcs.getSvnFileUrlMapping();
    List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(1, infos.size());
    Assert.assertEquals(parseUrl(myRepoUrl + "/trunk", false), infos.get(0).getUrl());

    runInAndVerifyIgnoreOutput("switch", branchUrl, myWorkingCopyDir.getPath());

    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);
    sleep(300);
    // no dirty scope externally provided! just VFS refresh
    clManager.ensureUpToDate(false);
    clManager.ensureUpToDate(false);  //first run queries one more update

    workingCopies = myVcs.getSvnFileUrlMapping();
    infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(1, infos.size());
    Assert.assertEquals(parseUrl(branchUrl, false), infos.get(0).getUrl());
  }

  @Test
  public void testExternalCommitInExternals() throws Exception {
    prepareExternal();

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalDir = new File(myWorkingCopyDir.getPath(), "source/external");
    final File file = new File(externalDir, "t11.txt");
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);

    final File mainFile = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
    final VirtualFile vfMain = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mainFile);

    renameFileInCommand(vf, "tt11.txt");
    renameFileInCommand(vfMain, "ss11.txt");

    myVcsDirtyScopeManager.markEverythingDirty();
    clManager.ensureUpToDate(false);
    Assert.assertEquals(2, clManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath());

    myWorkingCopyDir.refresh(false, true);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    imitateEvent(lfs.refreshAndFindFileByIoFile(sourceDir));
    imitateEvent(lfs.refreshAndFindFileByIoFile(externalDir));
    // no dirty scope externally provided! just VFS refresh
    clManager.ensureUpToDate(false);
    Assert.assertEquals(0, clManager.getChangesIn(myWorkingCopyDir).size());
  }
}
