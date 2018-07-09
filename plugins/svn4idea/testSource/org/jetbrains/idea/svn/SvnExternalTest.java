// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.api.Url;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assert.*;

public class SvnExternalTest extends SvnTestCase {
  private Url myMainUrl;
  private Url myExternalURL;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myMainUrl = parseUrl(myRepoUrl + "/root/source", false);
    myExternalURL = parseUrl(myRepoUrl + "/root/target", false);
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
    final SvnFileUrlMapping workingCopies = vcs.getSvnFileUrlMapping();
    final List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    assertEquals(2, infos.size());
    Set<Url> expectedUrls = new HashSet<>();
    if (myAnotherRepoUrl != null) {
      expectedUrls.add(parseUrl(myAnotherRepoUrl + "/root/target", false));
    } else {
      expectedUrls.add(myExternalURL);
    }
    expectedUrls.add(myMainUrl);

    for (RootUrlInfo info : infos) {
      expectedUrls.remove(info.getUrl());
    }
    assertTrue(expectedUrls.isEmpty());
  }

  protected void prepareInnerCopy() throws Exception {
    prepareInnerCopy(false);
  }

  @Test
  public void testInnerCopyDetected() throws Exception {
    prepareInnerCopy();

    final SvnFileUrlMapping workingCopies = vcs.getSvnFileUrlMapping();
    final List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    assertEquals(2, infos.size());
    Set<Url> expectedUrls = new HashSet<>();
    expectedUrls.add(myExternalURL);
    expectedUrls.add(myMainUrl);

    boolean sawInner = false;
    for (RootUrlInfo info : infos) {
      expectedUrls.remove(info.getUrl());
      sawInner |= NestedCopyType.inner.equals(info.getType());
    }
    assertTrue(expectedUrls.isEmpty());
    assertTrue(sawInner);
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

    assertNotNull(vf1);
    assertNotNull(vf2);

    VcsTestUtil.editFileInCommand(myProject, vf1, "test externals 123" + System.currentTimeMillis());
    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 123" + System.currentTimeMillis());

    refreshChanges();

    final Change change1 = changeListManager.getChange(vf1);
    final Change change2 = changeListManager.getChange(vf2);

    assertNotNull(change1);
    assertNotNull(change2);

    assertNotNull(change1.getBeforeRevision());
    assertNotNull(change2.getBeforeRevision());

    assertNotNull(change1.getAfterRevision());
    assertNotNull(change2.getAfterRevision());
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
    assertNotNull(externalVf);
  }

  private void setNewDirectoryMappings(final File sourceDir) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> vcsManager.setDirectoryMappings(
      Arrays.asList(new VcsDirectoryMapping(FileUtil.toSystemIndependentName(sourceDir.getPath()), vcs.getName()))));
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
    assertNotNull(externalVf);
    editFileInCommand(externalVf, "some new content");

    refreshChanges();

    final Change change = changeListManager.getChange(externalVf);
    assertNotNull(change);
    assertEquals(FileStatus.MODIFIED, change.getFileStatus());
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
