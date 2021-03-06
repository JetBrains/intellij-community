// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.InfoReliability;
import org.jetbrains.idea.svn.branchConfig.InfoStorage;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.integrate.QuickMerge;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.UsefulTestCase.assertExists;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.idea.svn.SvnPropertyKeys.MERGE_INFO;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.jetbrains.idea.svn.api.Revision.WORKING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SvnQuickMergeTest extends SvnTestCase {
  private String myBranchUrl;
  private File myBranchRoot;
  private VirtualFile myBranchVf;
  private SubTree myBranchTree;
  private SvnTestCase.SubTree myTree;

  @Override
  @Before
  public void before() throws Exception {
    super.before();

    myBranchUrl = prepareBranchesStructure();
    myBranchRoot = new File(myTempDirFixture.getTempDirPath(), "b1");

    runInAndVerifyIgnoreOutput("co", myBranchUrl, myBranchRoot.getPath());
    assertExists(myBranchRoot);
    myBranchVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myBranchRoot);
    assertNotNull(myBranchVf);

    myBranchTree = new SubTree(myBranchVf);
    myTree = new SubTree(myWorkingCopyDir);

    final SvnBranchConfigurationManager branchConfigurationManager = SvnBranchConfigurationManager.getInstance(myProject);
    final SvnBranchConfigurationNew configuration = new SvnBranchConfigurationNew();
    configuration.setTrunk(createUrl(myRepoUrl + "/trunk"));
    configuration.addBranches(createUrl(myRepoUrl + "/branches"),
                              new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
    branchConfigurationManager.setConfiguration(myWorkingCopyDir, configuration);

    //((ApplicationImpl) ApplicationManager.getApplication()).setRunPooledInTest(true);

    runInAndVerifyIgnoreOutput(virtualToIoFile(myWorkingCopyDir), "up");
  }

  @Test
  public void testSimpleMergeAllFromB1ToTrunk() throws Exception {
    VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, "edited in branch");
    runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch", myBranchTree.myS1File.getPath());

    waitQuickMerge(myBranchUrl, new QuickMergeTestInteraction(true, null));

    refreshChanges();

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = changeListManager.getChange(myTree.myS1File);
    assertNotNull(fileChange);
    assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = changeListManager.getChange(myWorkingCopyDir);
    assertNotNull(dirChange);
    assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
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
    Info info = vcs.getInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
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

    refreshChanges();

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = changeListManager.getChange(myTree.myS1File);
    assertNotNull(fileChange);
    assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = changeListManager.getChange(myWorkingCopyDir);
    assertNotNull(dirChange);
    assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    File file = virtualToIoFile(myWorkingCopyDir);
    PropertyValue value = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, WORKING);
    System.out.println(value.toString());
    assertEquals("/branches/b1:" + (numberBefore + 1), value.toString());
  }

  // this test is mainly to check revisions selection. at the moment we are not sure whether we support
  // trunk->b1->b2 merges between trunk and b2
  @Test
  public void testSelectRevisionsWithQuickSelect() throws Exception {
    Info info = vcs.getInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
    }

    // before copy
    Info info2 = vcs.getInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    assertNotNull(info2);
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

    refreshChanges();

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = changeListManager.getChange(myTree.myS2File);
    assertNotNull(fileChange);
    assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = changeListManager.getChange(myWorkingCopyDir);
    assertNotNull(dirChange);
    assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    File file = virtualToIoFile(myWorkingCopyDir);
    PropertyValue value = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, WORKING);
    System.out.println(value.toString());
    assertEquals("/branches/b2:" + (numberBeforeCopy + 2), value.toString());
  }

  @Test
  public void testSelectRevisions() throws Exception {
    Info info = vcs.getInfo(virtualToIoFile(myBranchTree.myS1File), WORKING);
    assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 10;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(virtualToIoFile(myBranchTree.myS1File)));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
    }

    QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction(true, lists ->
      lists.stream().filter(list -> numberBefore + 1 == list.getNumber() || numberBefore + 2 == list.getNumber()).collect(toList()));
    testInteraction.setMergeVariant(QuickMergeContentsVariants.select);

    waitQuickMerge(myBranchUrl, testInteraction);

    refreshChanges();

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = changeListManager.getChange(myTree.myS1File);
    assertNotNull(fileChange);
    assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = changeListManager.getChange(myWorkingCopyDir);
    assertNotNull(dirChange);
    assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    File file = virtualToIoFile(myWorkingCopyDir);
    PropertyValue value = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, WORKING);
    System.out.println(value.toString());
    assertEquals("/branches/b1:" + (numberBefore + 1) + "-" + (numberBefore + 2), value.toString());
  }

  private WCInfo getWcInfo() {
    WCInfo found = null;
    final File workingIoFile = virtualToIoFile(myWorkingCopyDir);
    final List<WCInfo> infos = vcs.getAllWcInfos();
    for (WCInfo info : infos) {
      if (FileUtil.filesEqual(workingIoFile, new File(info.getPath()))) {
        found = info;
        break;
      }
    }
    assertNotNull(found);
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

    refreshChanges();

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = changeListManager.getChange(myTree.myS1File);
    assertNotNull(fileChange);
    assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = changeListManager.getChange(myWorkingCopyDir);
    assertNotNull(dirChange);
    assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
  }

  private void waitQuickMerge(@NotNull String sourceUrl, @NotNull QuickMergeTestInteraction interaction) throws Exception {
    MergeContext mergeContext = new MergeContext(vcs, parseUrl(sourceUrl, false), getWcInfo(), Url.tail(sourceUrl), myWorkingCopyDir);
    QuickMerge quickMerge = new QuickMerge(mergeContext, interaction);

    getApplication().invokeAndWait(quickMerge::execute);

    quickMerge.waitForTasksToFinish();
    interaction.throwIfExceptions();
  }
}
