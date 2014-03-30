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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.mergeinfo.BranchInfo;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.List;

// TODO: Many tests in this class are written with direct SVNKit usage - could not utilize it for svn 1.8
public class SvnMergeInfoTest extends Svn17TestCase {
  private File myBranchVcsRoot;
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private WCInfo myWCInfo;
  private OneShotMergeInfoHelper myOneShotMergeInfoHelper;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myBranchVcsRoot = new File(myTempDirFixture.getTempDirPath(), "branch");
    myBranchVcsRoot.mkdir();

    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.setDirectoryMapping(myBranchVcsRoot.getAbsolutePath(), SvnVcs.VCS_NAME);

    VirtualFile vcsRoot = LocalFileSystem.getInstance().findFileByIoFile(myBranchVcsRoot);
    Node node = new Node(vcsRoot, SVNURL.parseURIEncoded(myRepoUrl + "/branch"), SVNURL.parseURIEncoded(myRepoUrl));
    RootUrlInfo root = new RootUrlInfo(node, WorkingCopyFormat.ONE_DOT_SIX, vcsRoot, null);
    myWCInfo = new WCInfo(root, true, SVNDepth.INFINITY);
    myOneShotMergeInfoHelper = new OneShotMergeInfoHelper(myProject, myWCInfo, myRepoUrl + "/trunk");

    SvnConfiguration.getInstance(myProject).setCheckNestedForQuickMerge(true);
//    AbstractVcs vcsFound = myProjectLevelVcsManager.findVcsByName(SvnVcs.VCS_NAME);
//    Assert.assertEquals(1, myProjectLevelVcsManager.getRootsUnderVcs(vcsFound).length);
  }

  @Test
  public void testSimpleNotMerged() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    
    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3

    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, myRepoUrl, myRepoUrl + "/branch", myRepoUrl + "/trunk", myRepoUrl + "/trunk");
    final SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, oneShotResult);
  }

  @Test
  public void testSimpleMerged() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    // rev 4: record as merged into branch
    runInAndVerifyIgnoreOutput("merge", "-c", "3", myRepoUrl + "/trunk", myBranchVcsRoot.getAbsolutePath(), "--record-only");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3".equals(data.getValue().getString()); 

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    final SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.MERGED, oneShotResult);
  }

  @Test
  public void testEmptyMergeinfoBlocks() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    // rev 4: record as merged into branch
    runInAndVerifyIgnoreOutput("merge", "-c", "3", myRepoUrl + "/trunk", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);
    // rev5: put blocking empty mergeinfo
    //runInAndVerifyIgnoreOutput("merge", "-c", "-3", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath(), "--record-only"));
    runInAndVerifyIgnoreOutput("merge", "-r", "3:2", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3".equals(data.getValue().getString());
    final SVNPropertyData dataFolder = wcClient.doGetProperty(new File(myBranchVcsRoot, "folder"), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert dataFolder != null && dataFolder.getValue() != null && "".equals(dataFolder.getValue().getString());

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    final SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, oneShotResult);  // todo
  }

  @Test
  public void testNonInheritableMergeinfo() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    // rev 4: record non inheritable merge
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3*", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3*".equals(data.getValue().getString());

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    final SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, oneShotResult);
  }

  @Test
  public void testOnlyImmediateInheritableMergeinfo() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());
    Thread.sleep(100);
    // rev4
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123\n4");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);

    // rev 4: record non inheritable merge
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3,4", myBranchVcsRoot.getAbsolutePath());
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3", new File(myBranchVcsRoot, "folder").getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && ("/trunk:3-4".equals(data.getValue().getString()) ||
                                                      "/trunk:3,4".equals(data.getValue().getString()));
    final SVNPropertyData dataFolder = wcClient.doGetProperty(new File(myBranchVcsRoot, "folder"), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert dataFolder != null && dataFolder.getValue() != null && "/trunk:3".equals(dataFolder.getValue().getString());

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList4 = changeListList.get(0);
    assert changeList4.getNumber() == 4;
    final SvnChangeList changeList3 = changeListList.get(1);
    assert changeList3.getNumber() == 3;

    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList3, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result);
    result = mergeChecker.checkList(changeList4, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList3);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.MERGED, oneShotResult);

    final SvnMergeInfoCache.MergeCheckResult oneShotResult1 = myOneShotMergeInfoHelper.checkList(changeList4);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, oneShotResult1);
  }

  @Test
  public void testTwoPaths() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    final File folder1 = new File(folder, "folder1");
    folder1.mkdir();
    final File f2 = new File(folder1, "f2.txt");
    f2.createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f2);
    VcsTestUtil.editFileInCommand(myProject, vf2, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);

    // rev 4: record non inheritable merge
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3", myBranchVcsRoot.getAbsolutePath());
    // this makes not merged for f2 path
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3*", new File(myBranchVcsRoot, "folder/folder1").getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3".equals(data.getValue().getString());
    final SVNPropertyData dataFolder = wcClient.doGetProperty(new File(myBranchVcsRoot, "folder/folder1"), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert dataFolder != null && dataFolder.getValue() != null && "/trunk:3*".equals(dataFolder.getValue().getString());

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList3 = changeListList.get(0);
    assert changeList3.getNumber() == 3;

    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList3, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    myOneShotMergeInfoHelper.prepare();
    final SvnMergeInfoCache.MergeCheckResult oneShotResult = myOneShotMergeInfoHelper.checkList(changeList3);
    Assert.assertEquals(SvnMergeInfoCache.MergeCheckResult.NOT_MERGED, oneShotResult);
  }

  @Test
  public void testWhenInfoInRepo() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File fullBranch = new File(myTempDirFixture.getTempDirPath(), "fullBranch");
    fullBranch.mkdir();

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    // this will be taken as branch wc root
    final File folder1 = new File(folder, "folder1");
    folder1.mkdir();
    final File f2 = new File(folder1, "f2.txt");
    f2.createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", fullBranch.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch/folder/folder1", myBranchVcsRoot.getAbsolutePath());

    // rev 3 : f2 changed
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f2);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    // rev 4: record as merged into branch using full branch WC
    runInAndVerifyIgnoreOutput("merge", "-c", "3", myRepoUrl + "/trunk", fullBranch.getAbsolutePath(), "--record-only");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", fullBranch.getAbsolutePath());

    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList3 = changeListList.get(0);
    assert changeList3.getNumber() == 3;

    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList3, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result);
  }

  @Test
  public void testMixedWorkingRevisions() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    final File f1 = new File(folder, "f1.txt");
    f1.createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    FileUtil.delete(trunk);
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/trunk", trunk.getAbsolutePath());
    runInAndVerifyIgnoreOutput("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath());

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    VcsTestUtil.editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", trunk.getAbsolutePath());

    // rev 4: record non inheritable merge
    runInAndVerifyIgnoreOutput("propset", "svn:mergeinfo", "/trunk:3", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);
    runInAndVerifyIgnoreOutput("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath());

    Thread.sleep(100);
    // ! no update!

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3".equals(data.getValue().getString());

    final SVNInfo f1info = wcClient.doInfo(new File(myBranchVcsRoot, "folder/f1.txt"), SVNRevision.UNDEFINED);
    assert f1info.getRevision().getNumber() == 2;

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk");
    SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    // and after update
    runInAndVerifyIgnoreOutput("up", myBranchVcsRoot.getAbsolutePath());
    Thread.sleep(100);

    mergeChecker.clear();
    result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result);
  }
}
