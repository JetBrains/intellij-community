// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.parseUrl;

public class SvnExternalCommitNoticedTest extends SvnTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    //System.setProperty(FileWatcher.PROPERTY_WATCHER_DISABLED, "false");
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testSimpleCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "test1");
    VcsTestUtil.editFileInCommand(myProject, tree.myS2File, "test2");
    VcsTestUtil.editFileInCommand(myProject, tree.myTargetFiles.get(1), "target1");

    refreshChanges();
    Assert.assertEquals(3, changeListManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    checkin();

    refreshVfs();
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    changeListManager.ensureUpToDate(false);
    Assert.assertEquals(0, changeListManager.getChangesIn(myWorkingCopyDir).size());
  }

  @Test
  public void testRenameDirCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    VcsTestUtil.renameFileInCommand(myProject, tree.myTargetDir, "aabbcc");

    refreshChanges();
    Assert.assertEquals(11, changeListManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    checkin();

    refreshVfs();
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    changeListManager.ensureUpToDate(false);
    Assert.assertEquals(0, changeListManager.getChangesIn(myWorkingCopyDir).size());
  }

  @Test
  public void testExternalSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/source/s1.txt", tree.myS1File.getPath());
    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/target", tree.myTargetDir.getPath());

    sleep(50);
    refreshVfs();
    imitateEvent(myWorkingCopyDir);
    // no dirty scope externally provided! just VFS refresh
    changeListManager.ensureUpToDate(false);

    Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myS1File));
    Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.myS2File));
    Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.mySourceDir));
    Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetDir));
    Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetFiles.get(1)));
  }

  @Test
  public void testExternalRootSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    vcs.invokeRefreshSvnRoots();
    changeListManager.ensureUpToDate(false);
    changeListManager.ensureUpToDate(false);
    SvnFileUrlMapping workingCopies = vcs.getSvnFileUrlMapping();
    List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(1, infos.size());
    Assert.assertEquals(parseUrl(myRepoUrl + "/trunk", false), infos.get(0).getUrl());

    runInAndVerifyIgnoreOutput("switch", branchUrl, myWorkingCopyDir.getPath());

    refreshVfs();
    imitateEvent(myWorkingCopyDir);
    sleep(300);
    // no dirty scope externally provided! just VFS refresh
    changeListManager.ensureUpToDate(false);
    changeListManager.ensureUpToDate(false);  //first run queries one more update

    workingCopies = vcs.getSvnFileUrlMapping();
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

    refreshChanges();
    Assert.assertEquals(2, changeListManager.getChangesIn(myWorkingCopyDir).size());

    TimeoutUtil.sleep(100);

    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath());

    refreshVfs();
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    imitateEvent(lfs.refreshAndFindFileByIoFile(sourceDir));
    imitateEvent(lfs.refreshAndFindFileByIoFile(externalDir));
    // no dirty scope externally provided! just VFS refresh
    changeListManager.ensureUpToDate(false);
    Assert.assertEquals(0, changeListManager.getChangesIn(myWorkingCopyDir).size());
  }
}
