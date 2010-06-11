package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
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

public class SvnMergeInfoTest extends SvnTestCase {
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

    myWCInfo = new WCInfo(myBranchVcsRoot.getAbsolutePath(), SVNURL.parseURIEncoded(myRepoUrl + "/branch"), WorkingCopyFormat.ONE_DOT_SIX,
                                     myRepoUrl, true, null, SVNDepth.INFINITY);
    myOneShotMergeInfoHelper = new OneShotMergeInfoHelper(myProject, myWCInfo, myRepoUrl + "/trunk");

    SvnConfiguration.getInstance(myProject).CHECK_NESTED_FOR_QUICK_MERGE = true;
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3

    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, myRepoUrl, myRepoUrl + "/branch", myRepoUrl + "/trunk", myRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    // rev 4: record as merged into branch
    verify(runSvn("merge", "-c", "3", myRepoUrl + "/trunk", myBranchVcsRoot.getAbsolutePath(), "--record-only"));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

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
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    // rev 4: record as merged into branch
    verify(runSvn("merge", "-c", "3", myRepoUrl + "/trunk", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);
    // rev5: put blocking empty mergeinfo
    //verify(runSvn("merge", "-c", "-3", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath(), "--record-only"));
    verify(runSvn("merge", "-r", "3:2", myRepoUrl + "/trunk/folder", new File(myBranchVcsRoot, "folder").getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

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
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    // rev 4: record non inheritable merge
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3*", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

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
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));
    Thread.sleep(100);
    // rev4
    editFileInCommand(myProject, vf, "123\n456\n123\n4");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);

    // rev 4: record non inheritable merge
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3,4", myBranchVcsRoot.getAbsolutePath()));
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3", new File(myBranchVcsRoot, "folder").getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

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
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f2);
    editFileInCommand(myProject, vf2, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);

    // rev 4: record non inheritable merge
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3", myBranchVcsRoot.getAbsolutePath()));
    // this makes not merged for f2 path
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3*", new File(myBranchVcsRoot, "folder/folder1").getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

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
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", fullBranch.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch/folder/folder1", myBranchVcsRoot.getAbsolutePath()));

    // rev 3 : f2 changed
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f2);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    // rev 4: record as merged into branch using full branch WC
    verify(runSvn("merge", "-c", "3", myRepoUrl + "/trunk", fullBranch.getAbsolutePath(), "--record-only"));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", fullBranch.getAbsolutePath()));

    Thread.sleep(100);
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList3 = changeListList.get(0);
    assert changeList3.getNumber() == 3;

    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
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

    verify(runSvn("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk"));
    verify(runSvn("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch"));

    FileUtil.delete(trunk);
    verify(runSvn("co", myRepoUrl + "/trunk", trunk.getAbsolutePath()));
    verify(runSvn("co", myRepoUrl + "/branch", myBranchVcsRoot.getAbsolutePath()));

    // rev 3
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f1);
    editFileInCommand(myProject, vf, "123\n456\n123");
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", trunk.getAbsolutePath()));

    // rev 4: record non inheritable merge
    verify(runSvn("propset", "svn:mergeinfo", "/trunk:3", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);
    verify(runSvn("ci", "-m", "test", myBranchVcsRoot.getAbsolutePath()));

    Thread.sleep(100);
    // ! no update!

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient wcClient = vcs.createWCClient();
    final SVNPropertyData data = wcClient.doGetProperty(myBranchVcsRoot, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    assert data != null && data.getValue() != null && "/trunk:3".equals(data.getValue().getString());

    final SVNInfo f1info = wcClient.doInfo(new File(myBranchVcsRoot, "folder/f1.txt"), SVNRevision.WORKING);
    assert f1info.getRevision().getNumber() == 2;

    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/trunk"), 0);

    final SvnChangeList changeList = changeListList.get(0);
    final String encodedRepoUrl = SVNURL.parseURIDecoded(myRepoUrl).toString();
    final BranchInfo mergeChecker =
      new BranchInfo(vcs, encodedRepoUrl, encodedRepoUrl + "/branch", encodedRepoUrl + "/trunk", encodedRepoUrl + "/trunk", vcs.createWCClient());
    SvnMergeInfoCache.MergeCheckResult result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(result);

    // and after update
    verify(runSvn("up", myBranchVcsRoot.getAbsolutePath()));
    Thread.sleep(100);

    mergeChecker.clear();
    result = mergeChecker.checkList(changeList, myBranchVcsRoot.getAbsolutePath());
    assert SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result);
  }
}
