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
package org.jetbrains.idea.svn16;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.Assert;
import org.jetbrains.idea.svn.*;
import org.junit.Test;

import java.io.File;
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

    final SvnFileUrlMapping workingCopies = myVcs.getSvnFileUrlMapping();
    final List<RootUrlInfo> infos = workingCopies.getAllWcInfos();
    Assert.assertEquals(2, infos.size());
    final Set<String> expectedUrls = new HashSet<>();
    expectedUrls.add(StringUtil.toLowerCase(myExternalURL));
    expectedUrls.add(StringUtil.toLowerCase(myMainUrl));

    for (RootUrlInfo info : infos) {
      expectedUrls.remove(StringUtil.toLowerCase(info.getAbsoluteUrl()));
    }
    Assert.assertTrue(expectedUrls.isEmpty());
  }

  private void prepareInnerCopy() throws Exception {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    clManager.stopEveryThingIfInTestMode();
    sleep(100);
    final File rootFile = new File(subTree.myRootDir.getPath());
    FileUtil.delete(rootFile);
    FileUtil.delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
    Assert.assertTrue(!rootFile.exists());
    sleep(200);
    myWorkingCopyDir.refresh(false, true);

    runInAndVerifyIgnoreOutput("co", myMainUrl);
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File innerDir = new File(sourceDir, "inner1/inner2/inner");
    runInAndVerifyIgnoreOutput("co", myExternalURL, innerDir.getPath());
    sleep(100);
    myWorkingCopyDir.refresh(false, true);
    // above is preparation

    // start change list manager again
    clManager.forceGoInTestMode();
    refreshSvnMappingsSynchronously();
    //clManager.ensureUpToDate(false);
    //clManager.ensureUpToDate(false);
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
}
