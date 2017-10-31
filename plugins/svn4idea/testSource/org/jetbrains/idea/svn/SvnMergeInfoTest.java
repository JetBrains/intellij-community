// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.mergeinfo.BranchInfo;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.idea.svn.SvnPropertyKeys.MERGE_INFO;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;

public class SvnMergeInfoTest extends Svn17TestCase {

  private static final String CONTENT1 = "123\n456\n123";
  private static final String CONTENT2 = "123\n456\n123\n4";

  private File myBranchVcsRoot;
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private WCInfo myWCInfo;
  private WCInfoWithBranches myWCInfoWithBranches;
  private OneShotMergeInfoHelper myOneShotMergeInfoHelper;

  private SvnVcs myVcs;
  private BranchInfo myMergeChecker;

  private File trunk;
  private File folder;
  private File folder1;
  private File f1;
  private File f2;

  private String myTrunkUrl;
  private String myBranchUrl;
  private MergeContext myMergeContext;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myTrunkUrl = myRepoUrl + "/trunk";
    myBranchUrl = myRepoUrl + "/branch";

    myBranchVcsRoot = new File(myTempDirFixture.getTempDirPath(), "branch");
    myBranchVcsRoot.mkdir();

    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.setDirectoryMapping(myBranchVcsRoot.getAbsolutePath(), SvnVcs.VCS_NAME);

    VirtualFile vcsRoot = LocalFileSystem.getInstance().findFileByIoFile(myBranchVcsRoot);
    Node node = new Node(vcsRoot, createUrl(myBranchUrl), createUrl(myRepoUrl));
    RootUrlInfo root = new RootUrlInfo(node, WorkingCopyFormat.ONE_DOT_SIX, vcsRoot, null);
    myWCInfo = new WCInfo(root, true, Depth.INFINITY);
    myMergeContext = new MergeContext(SvnVcs.getInstance(myProject), parseUrl(myTrunkUrl, false), myWCInfo, Url.tail(myTrunkUrl), vcsRoot);
    myOneShotMergeInfoHelper = new OneShotMergeInfoHelper(myMergeContext);

    myVcs = SvnVcs.getInstance(myProject);
    myVcs.getSvnConfiguration().setCheckNestedForQuickMerge(true);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final String repoUrl = createUrl(myRepoUrl, false).toString();
    myWCInfoWithBranches = new WCInfoWithBranches(myWCInfo, Collections.emptyList(), vcsRoot,
                                                  new WCInfoWithBranches.Branch(repoUrl + "/trunk"));
    myMergeChecker = new BranchInfo(myVcs, myWCInfoWithBranches, new WCInfoWithBranches.Branch(repoUrl + "/branch"));
  }

  @Test
  public void testSimpleNotMerged() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1);

    assertMergeResult(getTrunkChangeLists(), SvnMergeInfoCache.MergeCheckResult.NOT_MERGED);
  }

  @Test
  public void testSimpleMerged() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1);

    // rev 4: record as merged into branch
    recordMerge(myBranchVcsRoot, myTrunkUrl, "-c", "3");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);

    assertMergeInfo(myBranchVcsRoot, "/trunk:3");
    assertMergeResult(getTrunkChangeLists(), SvnMergeInfoCache.MergeCheckResult.MERGED);
  }

  @Test
  public void testEmptyMergeinfoBlocks() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1);

    // rev 4: record as merged into branch
    merge(myBranchVcsRoot, myTrunkUrl, "-c", "3");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);
    // rev5: put blocking empty mergeinfo
    //runInAndVerifyIgnoreOutput("merge", "-c", "-3", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath(), "--record-only"));
    merge(new File(myBranchVcsRoot, "folder"), myTrunkUrl + "/folder", "-r", "3:2");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);

    assertMergeInfo(myBranchVcsRoot, "/trunk:3");
    assertMergeInfo(new File(myBranchVcsRoot, "folder"), "");

    assertMergeResult(getTrunkChangeLists(), SvnMergeInfoCache.MergeCheckResult.NOT_MERGED);
  }

  @Test
  public void testNonInheritableMergeinfo() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1);

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3*");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);

    assertMergeInfo(myBranchVcsRoot, "/trunk:3*");

    assertMergeResult(getTrunkChangeLists(), SvnMergeInfoCache.MergeCheckResult.NOT_MERGED);
  }

  @Test
  public void testOnlyImmediateInheritableMergeinfo() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1, CONTENT1);
    // rev4
    editAndCommit(trunk, f1, CONTENT2);

    updateFile(myBranchVcsRoot);

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3,4");
    setMergeInfo(new File(myBranchVcsRoot, "folder"), "/trunk:3");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);

    assertMergeInfo(myBranchVcsRoot, "/trunk:3-4", "/trunk:3,4");
    assertMergeInfo(new File(myBranchVcsRoot, "folder"), "/trunk:3");

    final List<SvnChangeList> changeListList = getTrunkChangeLists();

    assertRevisions(changeListList, 4, 3);
    assertMergeResult(changeListList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, SvnMergeInfoCache.MergeCheckResult.MERGED);
  }

  @Test
  public void testTwoPaths() throws Exception {
    createTwoFolderStructure(myBranchVcsRoot);

    // rev 3
    editFile(f1);
    editFile(f2);
    commitFile(trunk);

    updateFile(myBranchVcsRoot);

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3");
    // this makes not merged for f2 path
    setMergeInfo(new File(myBranchVcsRoot, "folder/folder1"), "/trunk:3*");
    commitFile(myBranchVcsRoot);
    updateFile(myBranchVcsRoot);

    assertMergeInfo(myBranchVcsRoot, "/trunk:3");
    assertMergeInfo(new File(myBranchVcsRoot, "folder/folder1"), "/trunk:3*");

    final List<SvnChangeList> changeListList = getTrunkChangeLists();

    assertRevisions(changeListList, 3);
    assertMergeResult(changeListList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED);
  }

  @Test
  public void testWhenInfoInRepo() throws Exception {
    final File fullBranch = newFolder(myTempDirFixture.getTempDirPath(), "fullBranch");

    createTwoFolderStructure(fullBranch);
    // folder1 will be taken as branch wc root
    checkOutFile(myBranchUrl + "/folder/folder1", myBranchVcsRoot);

    // rev 3 : f2 changed
    editAndCommit(trunk, f2);

    // rev 4: record as merged into branch using full branch WC
    recordMerge(fullBranch, myTrunkUrl, "-c", "3");
    commitFile(fullBranch);
    updateFile(myBranchVcsRoot);

    final List<SvnChangeList> changeListList = getTrunkChangeLists();

    assertRevisions(changeListList, 3);
    assertMergeResult(changeListList.get(0), SvnMergeInfoCache.MergeCheckResult.MERGED);
  }

  @Test
  public void testMixedWorkingRevisions() throws Exception {
    createOneFolderStructure();

    // rev 3
    editAndCommit(trunk, f1);

    // rev 4: record non inheritable merge
    setMergeInfo(myBranchVcsRoot, "/trunk:3");
    commitFile(myBranchVcsRoot);
    // ! no update!

    assertMergeInfo(myBranchVcsRoot, "/trunk:3");

    final Info f1info = myVcs.getInfo(new File(myBranchVcsRoot, "folder/f1.txt"));
    assert f1info.getRevision().getNumber() == 2;

    final List<SvnChangeList> changeListList = getTrunkChangeLists();
    final SvnChangeList changeList = changeListList.get(0);

    assertMergeResult(changeList, SvnMergeInfoCache.MergeCheckResult.NOT_MERGED);

    // and after update
    updateFile(myBranchVcsRoot);
    myMergeChecker.clear();

    assertMergeResult(changeList, SvnMergeInfoCache.MergeCheckResult.MERGED);
  }

  private void createOneFolderStructure() throws InterruptedException, IOException {
    trunk = newFolder(myTempDirFixture.getTempDirPath(), "trunk");
    folder = newFolder(trunk, "folder");
    f1 = newFile(folder, "f1.txt");
    f2 = newFile(folder, "f2.txt");
    waitTime();

    importAndCheckOut(trunk);
  }

  private void createTwoFolderStructure(File branchFolder) throws InterruptedException, IOException {
    trunk = newFolder(myTempDirFixture.getTempDirPath(), "trunk");
    folder = newFolder(trunk, "folder");
    f1 = newFile(folder, "f1.txt");
    folder1 = newFolder(folder, "folder1");
    f2 = newFile(folder1, "f2.txt");
    waitTime();

    importAndCheckOut(trunk, branchFolder);
  }

  @NotNull
  private List<SvnChangeList> getTrunkChangeLists() throws com.intellij.openapi.vcs.VcsException {
    final CommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> provider = myVcs.getCommittedChangesProvider();

    return provider.getCommittedChanges(provider.createDefaultSettings(), new SvnRepositoryLocation(myTrunkUrl), 0);
  }

  private void importAndCheckOut(@NotNull File trunk) throws IOException {
    importAndCheckOut(trunk, myBranchVcsRoot);
  }

  private void importAndCheckOut(@NotNull File trunk, @NotNull File branch) throws IOException {
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myTrunkUrl);
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myTrunkUrl, myBranchUrl);

    FileUtil.delete(trunk);
    checkOutFile(myTrunkUrl, trunk);
    checkOutFile(myBranchUrl, branch);
  }

  @NotNull
  private VirtualFile editAndCommit(@NotNull File trunk, @NotNull File file) throws InterruptedException, IOException {
    return editAndCommit(trunk, file, CONTENT1);
  }

  @NotNull
  private VirtualFile editAndCommit(@NotNull File trunk, @NotNull File file, @NotNull String content)
    throws InterruptedException, IOException {
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);

    return editAndCommit(trunk, vf, content);
  }

  @NotNull
  private VirtualFile editAndCommit(@NotNull File trunk, @NotNull VirtualFile file, @NotNull String content)
    throws InterruptedException, IOException {
    editFile(file, content);
    commitFile(trunk);

    return file;
  }

  private void editFile(@NotNull File file) throws InterruptedException {
    editFile(file, CONTENT1);
  }

  private void editFile(@NotNull File file, @NotNull String content) throws InterruptedException {
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);

    editFile(vf, content);
  }

  private void editFile(@NotNull VirtualFile file, @NotNull String content) throws InterruptedException {
    VcsTestUtil.editFileInCommand(myProject, file, content);
    waitTime();
  }

  private void assertMergeInfo(@NotNull File file, @NotNull String... values) throws SvnBindException {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    PropertyValue propertyValue =
      vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), MERGE_INFO, false, Revision.WORKING);
    assert propertyValue != null;

    boolean result = false;

    for (String value : values) {
      result |= value.equals(propertyValue.toString());
    }

    assert result;
  }

  private void assertMergeResult(@NotNull List<SvnChangeList> changeLists, @NotNull SvnMergeInfoCache.MergeCheckResult... mergeResults)
    throws VcsException {
    myOneShotMergeInfoHelper.prepare();

    for (int i = 0; i < mergeResults.length; i++) {
      SvnChangeList changeList = changeLists.get(i);

      assertMergeResult(changeList, mergeResults[i]);
      assertMergeResultOneShot(changeList, mergeResults[i]);
    }
  }

  private void assertMergeResult(@NotNull SvnChangeList changeList, @NotNull SvnMergeInfoCache.MergeCheckResult mergeResult) {
    assert mergeResult.equals(myMergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath()));
  }

  private void assertMergeResultOneShot(@NotNull SvnChangeList changeList, @NotNull SvnMergeInfoCache.MergeCheckResult mergeResult) {
    Assert.assertEquals(mergeResult, myOneShotMergeInfoHelper.checkList(changeList));
  }

  private static void assertRevisions(@NotNull List<SvnChangeList> changeLists, @NotNull int... revisions) {
    for (int i = 0; i < revisions.length; i++) {
      assert changeLists.get(i).getNumber() == revisions[i];
    }
  }

  private void commitFile(@NotNull File file) throws IOException, InterruptedException {
    runInAndVerifyIgnoreOutput("ci", "-m", "test", file.getAbsolutePath());
    waitTime();
  }

  private void updateFile(@NotNull File file) throws IOException, InterruptedException {
    runInAndVerifyIgnoreOutput("up", file.getAbsolutePath());
    waitTime();
  }

  private void checkOutFile(@NotNull String url, @NotNull File directory) throws IOException {
    runInAndVerifyIgnoreOutput("co", url, directory.getAbsolutePath());
  }

  private void setMergeInfo(@NotNull File file, @NotNull String value) throws IOException, InterruptedException {
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", value, file.getAbsolutePath());
    waitTime();
  }

  private void merge(@NotNull File file, @NotNull String url, @NotNull String... revisions) throws IOException, InterruptedException {
    merge(file, url, false, revisions);
  }

  private void recordMerge(@NotNull File file, @NotNull String url, @NotNull String... revisions) throws IOException, InterruptedException {
    merge(file, url, true, revisions);
  }

  private void merge(@NotNull File file, @NotNull String url, boolean recordOnly, @NotNull String... revisions)
    throws IOException, InterruptedException {
    List<String> parameters = ContainerUtil.newArrayList();

    parameters.add("merge");
    ContainerUtil.addAll(parameters, revisions);
    parameters.add(url);
    parameters.add(file.getAbsolutePath());
    if (recordOnly) {
      parameters.add("--record-only");
    }

    runInAndVerifyIgnoreOutput(ArrayUtil.toObjectArray(parameters, String.class));
    waitTime();
  }

  @NotNull
  private static File newFile(File parent, String name) throws IOException {
    final File f1 = new File(parent, name);
    f1.createNewFile();
    return f1;
  }

  @NotNull
  private static File newFolder(String parent, String name) throws InterruptedException {
    final File trunk = new File(parent, name);
    trunk.mkdir();
    waitTime();
    return trunk;
  }

  @NotNull
  private static File newFolder(File parent, String name) throws InterruptedException {
    final File trunk = new File(parent, name);
    trunk.mkdir();
    waitTime();
    return trunk;
  }

  private static void waitTime() throws InterruptedException {
    Thread.sleep(100);
  }
}
