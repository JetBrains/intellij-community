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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.SvnTestCase;
import org.jetbrains.idea.svn.branchConfig.InfoReliability;
import org.jetbrains.idea.svn.branchConfig.InfoStorage;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.integrate.QuickMerge;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.util.stream.Collectors.toList;
import static org.tmatesoft.svn.core.wc.SVNRevision.UNDEFINED;
import static org.tmatesoft.svn.core.wc.SVNRevision.WORKING;

public class SvnQuickMergeTest extends Svn17TestCase {
  private SvnVcs myVcs;
  private String myBranchUrl;
  private File myBranchRoot;
  private VirtualFile myBranchVf;
  private SubTree myBranchTree;
  private ChangeListManager myChangeListManager;
  private SvnTestCase.SubTree myTree;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    myVcs = SvnVcs.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myBranchUrl = prepareBranchesStructure();
    myBranchRoot = new File(myTempDirFixture.getTempDirPath(), "b1");

    runInAndVerifyIgnoreOutput("co", myBranchUrl, myBranchRoot.getPath());
    Assert.assertTrue(myBranchRoot.exists());
    myBranchVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myBranchRoot);
    Assert.assertNotNull(myBranchVf);

    myBranchTree = new SubTree(myBranchVf);
    myTree = new SubTree(myWorkingCopyDir);

    final SvnBranchConfigurationManager branchConfigurationManager = SvnBranchConfigurationManager.getInstance(myProject);
    final SvnBranchConfigurationNew configuration = new SvnBranchConfigurationNew();
    configuration.setTrunkUrl(myRepoUrl + "/trunk");
    configuration.addBranches(myRepoUrl + "/branches",
                              new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
    branchConfigurationManager.setConfiguration(myWorkingCopyDir, configuration);

    //((ApplicationImpl) ApplicationManager.getApplication()).setRunPooledInTest(true);

    runInAndVerifyIgnoreOutput(virtualToIoFile(myWorkingCopyDir), "up");
    Thread.sleep(10);
  }

  @Test
  public void testSimpleMergeAllFromB1ToTrunk() throws Exception {
    VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, "edited in branch");
    runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch", myBranchTree.myS1File.getPath());

    waitQuickMerge(myBranchUrl, new QuickMergeTestInteraction(true, null));

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
  }

  // if we create branches like this:
  // trunk -> b1, b1->b2, b2->b3, b1->b4, then we should be able to merge between b1 and b2. some time before we had bug with it
  @Test
  public void testMergeBetweenDifferentTimeCreatedBranches() throws Exception {
    // b1 -> b2
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b2");
    // b2 -> b3
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myRepoUrl + "/branches/b2", myRepoUrl + "/branches/b3");
    // b1 -> b4
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b4");

    testSimpleMergeAllFromB1ToTrunk();
  }

  @Test
  public void testSelectRevisionsWithQuickSelectCheckForLocalChanges() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    AtomicReference<String> selectionError = new AtomicReference<>();
    QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction(true, lists -> {
      if (lists.get(3).getNumber() != numberBefore) {
        selectionError.set("wrong revision for copy statement: " + lists.get(3).getNumber());
      }
      return new SmartList<>(lists.get(2));  // get a change
    });
    testInteraction.setMergeVariant(QuickMergeContentsVariants.showLatest);

    waitQuickMerge(myBranchUrl, testInteraction);

    if (selectionError.get() != null) {
      throw new RuntimeException(selectionError.get());
    }

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(virtualToIoFile(myWorkingCopyDir), "svn:mergeinfo", UNDEFINED, WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b1:" + (numberBefore + 1), data.getValue().getString());
  }

  // this test is mainly to check revisions selection. at the moment we are not sure whether we support
  // trunk->b1->b2 merges between trunk and b2
  @Test
  public void testSelectRevisionsWithQuickSelect() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    // before copy
    final SVNInfo info2 = myVcs.getSvnKitManager().createWCClient().doInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    Assert.assertNotNull(info2);
    final long numberBeforeCopy = info2.getRevision().getNumber();

    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b2");

    // switch b1 to b2
    runInAndVerifyIgnoreOutput(myBranchRoot, "switch", myRepoUrl + "/branches/b2", myBranchRoot.getPath());
    myBranchTree = new SubTree(myBranchVf); //reload

    // one commit in b2 in s2 file
    VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS2File, "completely changed");
    runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in b2", myBranchTree.myS2File.getPath());

    AtomicReference<String> selectionError = new AtomicReference<>();
    QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction(true, lists -> {
      if (lists.get(1).getNumber() != numberBeforeCopy + 1) {
        selectionError.set("wrong revision for copy statement: " + lists.get(1).getNumber());
      }
      return new SmartList<>(lists.get(0));  // get a change
    });
    testInteraction.setMergeVariant(QuickMergeContentsVariants.showLatest);

    waitQuickMerge(myRepoUrl + "/branches/b2", testInteraction);

    if (selectionError.get() != null) {
      throw new RuntimeException(selectionError.get());
    }

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS2File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(virtualToIoFile(myWorkingCopyDir), "svn:mergeinfo", UNDEFINED, WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b2:" + (numberBeforeCopy + 2), data.getValue().getString());
  }

  @Test
  public void testSelectRevisions() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 10;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction(true, lists ->
      lists.stream().filter(list -> numberBefore + 1 == list.getNumber() || numberBefore + 2 == list.getNumber()).collect(toList()));
    testInteraction.setMergeVariant(QuickMergeContentsVariants.select);

    waitQuickMerge(myBranchUrl, testInteraction);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(virtualToIoFile(myWorkingCopyDir), "svn:mergeinfo", UNDEFINED, WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b1:" + (numberBefore + 1) + "-" + (numberBefore + 2), data.getValue().getString());
  }

  private WCInfo getWcInfo() {
    WCInfo found = null;
    final File workingIoFile = virtualToIoFile(myWorkingCopyDir);
    final List<WCInfo> infos = myVcs.getAllWcInfos();
    for (WCInfo info : infos) {
      if (FileUtil.filesEqual(workingIoFile, new File(info.getPath()))) {
        found = info;
        break;
      }
    }
    Assert.assertNotNull(found);
    return found;
  }

  @Test
  public void testSimpleMergeFromTrunkToB1() throws Exception {
    // change in trunk
    VcsTestUtil.editFileInCommand(myProject, myTree.myS1File, "903403240328");
    final File workingIoFile = virtualToIoFile(myWorkingCopyDir);
    runInAndVerifyIgnoreOutput(workingIoFile, "ci", "-m", "change in trunk", myTree.myS1File.getPath());

    final String trunkUrl = myRepoUrl + "/trunk";
    // switch this copy to b1
    runInAndVerifyIgnoreOutput(workingIoFile, "switch", myBranchUrl, workingIoFile.getPath());
    myTree = new SubTree(myWorkingCopyDir); //reload

    refreshSvnMappingsSynchronously();

    waitQuickMerge(trunkUrl, new QuickMergeTestInteraction(false, null));

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
  }

  private void waitQuickMerge(@NotNull String sourceUrl, @NotNull QuickMergeTestInteraction interaction) throws Exception {
    MergeContext mergeContext = new MergeContext(myVcs, sourceUrl, getWcInfo(), SVNPathUtil.tail(sourceUrl), myWorkingCopyDir);
    QuickMerge quickMerge = new QuickMerge(mergeContext, interaction);

    getApplication().invokeAndWait(quickMerge::execute);

    quickMerge.waitForTasksToFinish();
    interaction.throwIfExceptions();
  }
}
