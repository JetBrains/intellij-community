// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.hamcrest.Matchers.*;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SvnExternalCommitNoticedTest extends SvnTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testSimpleCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    editFileInCommand(tree.myS1File, "test1");
    editFileInCommand(tree.myS2File, "test2");
    editFileInCommand(tree.myTargetFiles.get(1), "target1");
    refreshChanges();
    assertChanges(3);

    checkin();
    refreshVfs();
    changeListManager.ensureUpToDate();
    assertNoChanges();
  }

  @Test
  public void testRenameDirCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    renameFileInCommand(tree.myTargetDir, "aabbcc");
    refreshChanges();
    assertChanges(tree.myTargetFiles.size() + 1);

    checkin();

    refreshVfs();
    changeListManager.ensureUpToDate();
    assertNoChanges();
  }

  @Test
  public void testExternalSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/source/s1.txt", tree.myS1File.getPath());
    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/target", tree.myTargetDir.getPath());

    refreshVfs();
    changeListManager.ensureUpToDate();
    assertStatus(tree.myS1File, FileStatus.SWITCHED);
    assertStatus(tree.myS2File, FileStatus.NOT_CHANGED);
    assertStatus(tree.mySourceDir, FileStatus.NOT_CHANGED);
    assertStatus(tree.myTargetDir, FileStatus.SWITCHED);
    assertStatus(tree.myTargetFiles.get(1), FileStatus.SWITCHED);
  }

  @Test
  public void testExternalRootSwitch() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    new SubTree(myWorkingCopyDir);
    refreshSvnMappingsSynchronously();
    List<RootUrlInfo> infos = vcs.getSvnFileUrlMapping().getAllWcInfos();
    assertThat(map(infos, RootUrlInfo::getUrl), containsInAnyOrder(ar(parseUrl(myRepoUrl + "/trunk", false))));

    runInAndVerifyIgnoreOutput("switch", branchUrl, myWorkingCopyDir.getPath());

    refreshVfs();
    changeListManager.ensureUpToDate();
    vcs.getCopiesRefreshManager().waitCurrentRequest();
    infos = vcs.getSvnFileUrlMapping().getAllWcInfos();
    assertThat(map(infos, RootUrlInfo::getUrl), containsInAnyOrder(ar(parseUrl(branchUrl, false))));
  }

  @Test
  public void testExternalCommitInExternals() throws Exception {
    prepareExternal();
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalDir = new File(myWorkingCopyDir.getPath(), "source/external");
    final VirtualFile vf = notNull(myWorkingCopyDir.findFileByRelativePath("source/external/t11.txt"));
    final VirtualFile vfMain = notNull(myWorkingCopyDir.findFileByRelativePath("source/s1.txt"));
    renameFileInCommand(vf, "tt11.txt");
    renameFileInCommand(vfMain, "ss11.txt");
    refreshChanges();
    assertChanges(2);

    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath());

    refreshVfs();
    changeListManager.ensureUpToDate();
    assertNoChanges();
  }

  private void assertNoChanges() {
    assertThat(changeListManager.getChangesIn(myWorkingCopyDir), is(empty()));
  }

  private void assertChanges(int count) {
    assertThat(changeListManager.getChangesIn(myWorkingCopyDir), hasSize(count));
  }

  private void assertStatus(@NotNull VirtualFile file, @NotNull FileStatus status) {
    assertEquals(status, changeListManager.getStatus(file));
  }
}
