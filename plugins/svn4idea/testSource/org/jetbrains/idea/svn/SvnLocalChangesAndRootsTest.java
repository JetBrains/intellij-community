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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SvnLocalChangesAndRootsTest extends Svn17TestCase {
  private File myAlienRoot;
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private ChangeListManager myClManager;

  @Override
  public void setUp() {
    /*super.setUp();

    myAlienRoot = new File(myTempDirFixture.getTempDirPath(), "alien");
    myAlienRoot.mkdir();

    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.setDirectoryMapping(myAlienRoot.getAbsolutePath(), SvnVcs.VCS_NAME);
    myProjectLevelVcsManager.updateActiveVcss();

    myClManager = ChangeListManager.getInstance(myProject);*/
  }

  // a subtree just marked under VCS root but not content root
  private class AlienTree {
    private final File myDir;
    private final File myFile;
    private final File myUnversioned;
    private final File myIgnored;

    public AlienTree(final String base) throws IOException {
      final String name = "alien";
      myDir = new File(base, name);
      myDir.mkdir();
      sleep100();

      myFile = new File(myDir, "file.txt");
      myFile.createNewFile();
      sleep100();

      runInAndVerifyIgnoreOutput("import", "-m", "test", myDir.getAbsolutePath(), myRepoUrl + "/" + name);
      FileUtil.delete(myDir);
      runInAndVerifyIgnoreOutput("co", myRepoUrl + "/" + name, myDir.getAbsolutePath());

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
      TimeoutUtil.sleep(100);
    }
  }

  @Override
  public void tearDown() {
    //super.tearDown();
  }

  @Test
  public void testAlienRoot() {
    /*final AlienTree alienTree = new AlienTree(myAlienRoot.getAbsolutePath());

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile tmpVf = LocalFileSystem.getInstance().findFileByIoFile(new File(myTempDirFixture.getTempDirPath()));
    Assert.assertNotNull(tmpVf);

    sleep100();

    tmpVf.refresh(false, true);
    
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh(myRefreshCopiesStub);

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
    myClManager.isUnversioned(vfIgn);*/
  }

  private class SubTree {
    private final VirtualFile myOuterDir;
    private final VirtualFile myOuterFile;
    private final VirtualFile myRootDir;
    private final VirtualFile myInnerFile;
    private VirtualFile myNonVersionedUpper;
    private final VirtualFile myMappingTarget;

    private SubTree(final VirtualFile base, final VirtualFile projectRoot) {
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
      final List<VcsDirectoryMapping> newMappings = new ArrayList<>(mappings.size());

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
  public void testSvnVcsRootAbove() {
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
  public void testFakeScopeDontBreakTheView() {
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
    TimeoutUtil.sleep(100);
  }
}
