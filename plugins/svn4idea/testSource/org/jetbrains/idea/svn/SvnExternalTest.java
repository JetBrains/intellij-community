/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/12/12
 * Time: 10:24 AM
 */
public class SvnExternalTest extends Svn17TestCase {
  private ChangeListManagerImpl clManager;
  private SvnVcs myVcs;
  private String myMainUrl;
  private String myExternalURL;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    clManager = (ChangeListManagerImpl) ChangeListManager.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myVcs = SvnVcs.getInstance(myProject);
    myMainUrl = myRepoUrl + "/root/source";
    myExternalURL = myRepoUrl + "/root/target";
  }

  @Test
  public void testExternalCopyIsDetected() throws Exception {
    prepareExternal();
    externalCopyIsDetectedImpl();
  }

  @Test
  public void testExternalCopyIsDetectedAnotherRepo() throws Exception {
    prepareExternal(true, true, true);
    externalCopyIsDetectedImpl();
  }

  private void externalCopyIsDetectedImpl() {
    final SvnFileUrlMapping workingCopies = myVcs.getSvnFileUrlMapping();
    final List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(2, infos.size());
    final Set<String> expectedUrls = new HashSet<>();
    if (myAnotherRepoUrl != null) {
      expectedUrls.add(StringUtil.toLowerCase(myAnotherRepoUrl + "/root/target"));
    } else {
      expectedUrls.add(StringUtil.toLowerCase(myExternalURL));
    }
    expectedUrls.add(StringUtil.toLowerCase(myMainUrl));

    for (RootUrlInfo info : infos) {
      expectedUrls.remove(StringUtil.toLowerCase(info.getAbsoluteUrl()));
    }
    Assert.assertTrue(expectedUrls.isEmpty());
  }

  protected void prepareInnerCopy() throws Exception {
    prepareInnerCopy(false);
  }

  @Test
  public void testInnerCopyDetected() throws Exception {
    prepareInnerCopy();

    final SvnFileUrlMapping workingCopies = myVcs.getSvnFileUrlMapping();
    final List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(2, infos.size());
    final Set<String> expectedUrls = new HashSet<>();
    expectedUrls.add(StringUtil.toLowerCase(myExternalURL));
    expectedUrls.add(StringUtil.toLowerCase(myMainUrl));

    boolean sawInner = false;
    for (RootUrlInfo info : infos) {
      expectedUrls.remove(StringUtil.toLowerCase(info.getAbsoluteUrl()));
      sawInner |= NestedCopyType.inner.equals(info.getType());
    }
    Assert.assertTrue(expectedUrls.isEmpty());
    Assert.assertTrue(sawInner);
  }

  @Test
  public void testSimpleExternalsStatus() throws Exception {
    prepareExternal();
    simpleExternalStatusImpl();
  }

  @Test
  public void testSimpleExternalsAnotherStatus() throws Exception {
    prepareExternal(true, true, true);
    simpleExternalStatusImpl();
  }

  private void simpleExternalStatusImpl() {
    final File sourceFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "s1.txt");
    final File externalFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "external" + File.separator + "t12.txt");

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final VirtualFile vf1 = lfs.refreshAndFindFileByIoFile(sourceFile);
    final VirtualFile vf2 = lfs.refreshAndFindFileByIoFile(externalFile);

    Assert.assertNotNull(vf1);
    Assert.assertNotNull(vf2);

    VcsTestUtil.editFileInCommand(myProject, vf1, "test externals 123" + System.currentTimeMillis());
    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 123" + System.currentTimeMillis());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);

    final Change change1 = clManager.getChange(vf1);
    final Change change2 = clManager.getChange(vf2);

    Assert.assertNotNull(change1);
    Assert.assertNotNull(change2);

    Assert.assertNotNull(change1.getBeforeRevision());
    Assert.assertNotNull(change2.getBeforeRevision());

    Assert.assertNotNull(change1.getAfterRevision());
    Assert.assertNotNull(change2.getAfterRevision());
  }

  @Test
  public void testUpdatedCreatedExternalFromIDEA() throws Exception {
    prepareExternal(false, false, false);
    updatedCreatedExternalFromIDEAImpl();
  }

  @Test
  public void testUpdatedCreatedExternalFromIDEAAnother() throws Exception {
    prepareExternal(false, false, true);
    updatedCreatedExternalFromIDEAImpl();
  }

  private void updatedCreatedExternalFromIDEAImpl() {
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    setNewDirectoryMappings(sourceDir);
    imitUpdate(myProject);

    final File externalFile = new File(sourceDir, "external/t11.txt");
    final VirtualFile externalVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalFile);
    Assert.assertNotNull(externalVf);
  }

  private void setNewDirectoryMappings(final File sourceDir) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ProjectLevelVcsManager.getInstance(myProject).setDirectoryMappings(
          Arrays.asList(new VcsDirectoryMapping(FileUtil.toSystemIndependentName(sourceDir.getPath()), myVcs.getName())));
      }
    });
  }

  @Test
  public void testUncommittedExternalStatus() throws Exception {
    prepareExternal(false, true, false);
    uncommittedExternalStatusImpl();
  }

  @Test
  public void testUncommittedExternalStatusAnother() throws Exception {
    prepareExternal(false, true, true);
    uncommittedExternalStatusImpl();
  }

  private void uncommittedExternalStatusImpl() {
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalFile = new File(sourceDir, "external/t11.txt");
    final VirtualFile externalVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalFile);
    Assert.assertNotNull(externalVf);
    editFileInCommand(externalVf, "some new content");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);

    final Change change = clManager.getChange(externalVf);
    Assert.assertNotNull(change);
    Assert.assertEquals(FileStatus.MODIFIED, change.getFileStatus());
  }

  @Test
  public void testUncommittedExternalCopyIsDetected() throws Exception {
    prepareExternal(false, false, false);
    uncommittedExternalCopyIsDetectedImpl();
  }

  @Test
  public void testUncommittedExternalCopyIsDetectedAnother() throws Exception {
    prepareExternal(false, false, true);
    uncommittedExternalCopyIsDetectedImpl();
  }

  private void uncommittedExternalCopyIsDetectedImpl() {
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    setNewDirectoryMappings(sourceDir);
    imitUpdate(myProject);
    refreshSvnMappingsSynchronously();

    externalCopyIsDetectedImpl();
  }
}
