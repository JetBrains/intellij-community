package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vcs.changes.pending.DuringChangeListManagerUpdateTestScheme;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class SvnLocalChangesAndRootsTest extends SvnTestCase {
  private File myAlienRoot;
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private ChangeListManager myClManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myAlienRoot = new File(myTempDirFixture.getTempDirPath(), "alien");
    myAlienRoot.mkdir();

    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.setDirectoryMapping(myAlienRoot.getAbsolutePath(), SvnVcs.VCS_NAME);
    myProjectLevelVcsManager.updateActiveVcss();

    myClManager = ChangeListManager.getInstance(myProject);
  }

  // a subtree just marked under VCS root but not content root
  private class AlienTree {
    private File myDir;
    private File myFile;
    private File myUnversioned;
    private File myIgnored;

    public AlienTree(final String base) throws IOException {
      final String name = "alien";
      myDir = new File(base, name);
      myDir.mkdir();
      sleep100();

      myFile = new File(myDir, "file.txt");
      myFile.createNewFile();
      sleep100();

      verify(runSvn("import", "-m", "test", myDir.getAbsolutePath(), myRepoUrl + "/" + name));
      FileUtil.delete(myDir);
      verify(runSvn("co", myRepoUrl + "/" + name, myDir.getAbsolutePath()));

      myUnversioned = new File(myDir, "unversioned.txt");
      myFile.createNewFile();
      sleep100();
      myIgnored = new File(myDir, "ignored.txt");
      myFile.createNewFile();
      sleep100();

      // ignore
      myClManager.setFilesToIgnore(IgnoredBeanFactory.withMask("ignored*"));
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

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testAlienRoot() throws Throwable {
    final AlienTree alienTree = new AlienTree(myAlienRoot.getAbsolutePath());

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile tmpVf = LocalFileSystem.getInstance().findFileByIoFile(new File(myTempDirFixture.getTempDirPath()));
    Assert.assertNotNull(tmpVf);

    tmpVf.refresh(false, true);
    
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh();

    Assert.assertEquals(2, vcs.getSvnFileUrlMapping().getAllWcInfos().size());

    final VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(alienTree.myFile);
    editFileInCommand(myProject, vf, "78");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myClManager.ensureUpToDate(false);

    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {vf},
      myClManager.getDefaultListName(), myClManager);

    final VirtualFile vfUnv = LocalFileSystem.getInstance().findFileByIoFile(alienTree.myUnversioned);
    myClManager.isUnversioned(vfUnv);

    final VirtualFile vfIgn = LocalFileSystem.getInstance().findFileByIoFile(alienTree.myIgnored);
    myClManager.isUnversioned(vfIgn);
  }

  private class SubTree {
    private VirtualFile myOuterDir;
    private VirtualFile myOuterFile;
    private VirtualFile myRootDir;
    private VirtualFile myInnerFile;
    private VirtualFile myNonVersionedUpper;

    private SubTree(final VirtualFile base) throws Throwable {
      myOuterDir = createDirInCommand(base, "outer");
      myOuterFile = createFileInCommand(myOuterDir, "outer.txt", "123");
      myRootDir = createDirInCommand(myOuterDir, "root");
      myInnerFile = createFileInCommand(myRootDir, "inner.txt", "321");
      myNonVersionedUpper = createFileInCommand(base, "nonVersioned.txt", "135");
    }
  }

  @Test
  public void testSvnVcsRootAbove() throws Throwable {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final SubTree subTree = new SubTree(myWorkingCopyDir);

    checkin();

    editFileInCommand(myProject, subTree.myOuterFile, "***");
    editFileInCommand(myProject, subTree.myInnerFile, "*&*");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);
  }

  @Test
  public void testFakeScopeDontBreakTheView() throws Throwable {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final SubTree subTree = new SubTree(myWorkingCopyDir);

    checkin();

    editFileInCommand(myProject, subTree.myOuterFile, "***");
    editFileInCommand(myProject, subTree.myInnerFile, "*&*");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);

    VcsDirtyScopeManagerImpl.getInstance(myProject).fileDirty(subTree.myNonVersionedUpper);
    myClManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myOuterFile, subTree.myInnerFile},
      myClManager.getDefaultListName(), myClManager);
  }
}
