package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SvnRootAboveTest extends SvnTestCase {
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private ChangeListManager myClManager;

  private File myLocalWcRoot;
  private File myProjectRoot;
  private File myModuleRoot;

  @Override
  public void setUp() throws Exception {
    /*final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myTempDirFixture = fixtureFactory.createTempDirTestFixture();
    myTempDirFixture.setUp();

    final File svnRoot = new File(myTempDirFixture.getTempDirPath(), "svnroot");
    svnRoot.mkdir();

    File pluginRoot = new File(PluginPathManager.getPluginHomePath("svn4idea"));
    if (!pluginRoot.isDirectory()) {
      // try standalone mode
      Class aClass = SvnTestCase.class;
      String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      pluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
    }
    myClientBinaryPath = new File(pluginRoot, "testData/svn/bin");

    ZipUtil.extract(new File(pluginRoot, "testData/svn/newrepo.zip"), svnRoot, null);

    // real WC root
    myLocalWcRoot = new File(myTempDirFixture.getTempDirPath(), "wcroot");
    myLocalWcRoot.mkdir();

    myRepoUrl = "file:///" + FileUtil.toSystemIndependentName(svnRoot.getPath());
    verify(runSvn("co", myRepoUrl, myLocalWcRoot.getAbsolutePath()));

    // one level below - project root dir
    sleep100();
    myProjectRoot = new File(myLocalWcRoot, "projectRoot");
    myProjectRoot.mkdir();
    // one more level below - content root
    sleep100();
    myModuleRoot = new File(myProjectRoot, "moduleRoot");
    myModuleRoot.mkdir();
    sleep100();

    // set current directory
    verify(runArbitrary("cd", new String[] {myProjectRoot.getAbsolutePath()}));
    initProject(myModuleRoot);
    verify(runArbitrary("cd", new String[] {myTempDirFixture.getTempDirPath()}));

    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.setDirectoryMapping(myLocalWcRoot.getAbsolutePath(), SvnVcs.VCS_NAME);
    myProjectLevelVcsManager.updateActiveVcss();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh(myRefreshCopiesStub);

    myClManager = ChangeListManager.getInstance(myProject);*/
  }

  private class SubTree {
    private final VirtualFile myOuterDir;
    private final VirtualFile myOuterFile;
    private final VirtualFile myRootDir;
    private final VirtualFile myInnerFile;
    private VirtualFile myNonVersionedUpper;
    private final VirtualFile myMappingTarget;

    private SubTree(final VirtualFile base, final VirtualFile projectRoot) throws Throwable {
      // todo +-
      myMappingTarget = projectRoot.getParent();
      myOuterDir = createDirInCommand(myMappingTarget, "outer" + System.currentTimeMillis());
      myOuterFile = createFileInCommand(myOuterDir, "outer.txt", "123");

      myRootDir = createDirInCommand(base, "root");
      myInnerFile = createFileInCommand(myRootDir, "inner.txt", "321");

      myNonVersionedUpper = createFileInCommand(base, "nonVersioned.txt", "135");

      // correct mappings
      final List<VcsDirectoryMapping> mappings = myProjectLevelVcsManager.getDirectoryMappings();
      final String basePath = base.getPath();
      final List<VcsDirectoryMapping> newMappings = new ArrayList<VcsDirectoryMapping>(mappings.size());

      for (VcsDirectoryMapping mapping : mappings) {
        if (! basePath.equals(mapping.getDirectory())) {
          newMappings.add(mapping);
        }
      }
      newMappings.add(new VcsDirectoryMapping(myMappingTarget.getPath(), SvnVcs.VCS_NAME));
      myProjectLevelVcsManager.setDirectoryMappings(newMappings);
      myProjectLevelVcsManager.updateActiveVcss();
      sleep100();
    }

    public void createNonVers() {
      myNonVersionedUpper = createFileInCommand(myMappingTarget, "nonVersioned.txt", "135");
    }
  }
  
  @Test
  public void testSvnVcsRootAbove() throws Throwable {
    /*enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final SubTree subTree = new SubTree(myWorkingCopyDir, myProject.getBaseDir());

    checkin();

    subTree.createNonVers();

    editFileInCommand(myProject, subTree.myOuterFile, "***");
    editFileInCommand(myProject, subTree.myInnerFile, "*&*");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);*/
  }

  @Test
  public void testFakeScopeDontBreakTheView() throws Throwable {
    /*enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final SubTree subTree = new SubTree(myWorkingCopyDir, myProject.getBaseDir());

    checkin();

    subTree.createNonVers();

    editFileInCommand(myProject, subTree.myOuterFile, "***");
    editFileInCommand(myProject, subTree.myInnerFile, "*&*");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);

    VcsDirtyScopeManagerImpl.getInstance(myProject).fileDirty(subTree.myNonVersionedUpper);
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);*/
  }

  private void sleep100() {
    try {
      Thread.sleep(100);
    }
    catch (InterruptedException e) {
      //
    }
  }
}
