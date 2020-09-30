// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.api.Url;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assert.*;

public class SvnExternalTest extends SvnTestCase {
  private Url myMainUrl;
  private Url myExternalURL;

  @Override
  public void before() throws Exception {
    super.before();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myMainUrl = myRepositoryUrl.appendPath("root/source", false);
    myExternalURL = myRepositoryUrl.appendPath("root/target", false);
  }

  @Test
  public void testExternalCopyIsDetected() throws Exception {
    prepareExternal();
    assertWorkingCopies();
  }

  @Test
  public void testExternalCopyIsDetectedAnotherRepo() throws Exception {
    prepareExternal(true, true, true);
    assertWorkingCopies();
  }

  private void assertWorkingCopies() {
    List<RootUrlInfo> infos = vcs.getSvnFileUrlMapping().getAllWcInfos();
    Url[] urls = ar(myAnotherRepoUrl != null ? parseUrl(myAnotherRepoUrl + "/root/target", false) : myExternalURL, myMainUrl);

    assertThat(map(infos, RootUrlInfo::getUrl), containsInAnyOrder(urls));
  }

  @Test
  public void testInnerCopyDetected() throws Exception {
    prepareInnerCopy(false);

    assertWorkingCopies();
    assertThat(map(vcs.getSvnFileUrlMapping().getAllWcInfos(), RootUrlInfo::getType), hasItem(equalTo(NestedCopyType.inner)));
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

    editFileInCommand(vf1, "test externals 123" + System.currentTimeMillis());
    editFileInCommand(vf2, "test externals 123" + System.currentTimeMillis());
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
    imitUpdate();

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalFile = new File(sourceDir, "external/t11.txt");
    final VirtualFile externalVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalFile);
    assertNotNull(externalVf);
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
    imitUpdate();
    refreshSvnMappingsSynchronously();

    assertWorkingCopies();
  }
}
