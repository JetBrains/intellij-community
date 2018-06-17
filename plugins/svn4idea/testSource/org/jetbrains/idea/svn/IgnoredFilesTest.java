// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.ui.UIUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Here we check the situation when there's no working copy - nevertheless we think that ignored on IDEA level files should be
 * marked even under unversioned directories
 * @author irengrig
 */
public class IgnoredFilesTest extends SvnTestCase {
  private SvnVcs myVcs;
  private ProjectLevelVcsManagerImpl myVcsManager;
  private ChangeListManager myChangeListManager;
  private LocalFileSystem myLocalFileSystem;
  private TempDirTestFixture myTempDirFixture;
  private File myClientRoot;
  private VcsDirtyScopeManager myVcsDirtyScopeManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
        myTempDirFixture = fixtureFactory.createTempDirTestFixture();
        myTempDirFixture.setUp();

        myClientRoot = new File(myTempDirFixture.getTempDirPath(), "clientroot");
        myClientRoot.mkdir();

        initProject(myClientRoot, this.getTestName());

        ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();

        myChangeListManager = ChangeListManager.getInstance(myProject);
        myVcs = SvnVcs.getInstance(myProject);
        myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
        myVcsManager.registerVcs(myVcs);
        myVcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), myVcs.getName());

        myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
        myLocalFileSystem = LocalFileSystem.getInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @After
  public void tearDown() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        myVcsManager.unregisterVcs(myVcs);
        myVcsManager = null;
        myVcs = null;

        tearDownProject();
        if (myTempDirFixture != null) {
          myTempDirFixture.tearDown();
          myTempDirFixture = null;
        }
        FileUtil.delete(myClientRoot);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  // they all blink now

  @Test
  public void testFileIsIgnored() throws Exception {
    final String filePath1 = myClientRoot.getPath() + "/a";
    final File file = new File(filePath1);
    file.createNewFile();

    final IgnoredFileBean ignoredFileBean = IgnoredBeanFactory.ignoreFile(filePath1, myProject);
    myChangeListManager.addFilesToIgnore(ignoredFileBean);

    dirty();

    final VirtualFile vf = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    Assert.assertNotNull(vf);
    Assert.assertTrue(myChangeListManager.isIgnoredFile(vf));
  }

  private void dirty() {
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.scheduleUpdate();
    myChangeListManager.ensureUpToDate(false);
  }

  @Test
  public void testDirIsIgnored() throws Exception {
    //final String dirPath1 = myClientRoot.getPath() + "/a";
    final File dir = new File(myClientRoot, "a");
    dir.mkdir();
    final File innerDir = new File(dir, "innerDir");
    innerDir.mkdir();
    final File file1 = new File(innerDir, "file1");
    final File file2 = new File(innerDir, "file2");
    file1.createNewFile();
    file2.createNewFile();

    final VirtualFile innerVf = myLocalFileSystem.refreshAndFindFileByIoFile(innerDir);
    final VirtualFile vf1 = myLocalFileSystem.refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = myLocalFileSystem.refreshAndFindFileByIoFile(file2);

    final IgnoredFileBean ignoredFileBean = IgnoredBeanFactory.ignoreUnderDirectory(FileUtil.toSystemIndependentName(dir.getPath()), myProject);
    myChangeListManager.addFilesToIgnore(ignoredFileBean);

    dirty();

    assertFoundAndIgnored(innerVf);
    assertFoundAndIgnored(vf1);
    assertFoundAndIgnored(vf2);
  }

  @Test
  public void testPatternIsIgnored() throws Exception {
    final String dirPath1 = myClientRoot.getPath() + "/a";
    final File dir = new File(myClientRoot, "a");
    dir.mkdir();
    final File innerDir = new File(dir, "innerDir");
    innerDir.mkdir();
    final File file1 = new File(innerDir, "file1");
    final File file2 = new File(innerDir, "file2");
    file1.createNewFile();
    file2.createNewFile();

    final VirtualFile innerVf = myLocalFileSystem.refreshAndFindFileByIoFile(innerDir);
    final VirtualFile vf1 = myLocalFileSystem.refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = myLocalFileSystem.refreshAndFindFileByIoFile(file2);

    final IgnoredFileBean ignoredFileBean = IgnoredBeanFactory.withMask("file*");
    myChangeListManager.addFilesToIgnore(ignoredFileBean);

    dirty();

    Assert.assertNotNull(innerVf);
    Assert.assertFalse(myChangeListManager.isIgnoredFile(innerVf));
    Assert.assertEquals(FileStatus.UNKNOWN, myChangeListManager.getStatus(innerVf));
    Assert.assertTrue(myChangeListManager.isUnversioned(innerVf));

    assertFoundAndIgnored(vf1);
    assertFoundAndIgnored(vf2);
  }

  void assertFoundAndIgnored(final VirtualFile vf) {
    Assert.assertNotNull(vf);
    Assert.assertTrue(myChangeListManager.isIgnoredFile(vf));
    Assert.assertEquals(FileStatus.IGNORED, myChangeListManager.getStatus(vf));
  }
}
