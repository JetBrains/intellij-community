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

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNPropertyValue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author irengrig
 *         Date: 12/20/10
 *         Time: 5:05 PM
 */
public class SvnIgnoreTest extends Svn17TestCase {
  private ChangeListManager clManager;
  private SvnVcs myVcs;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    clManager = ChangeListManager.getInstance(myProject);
    myVcs = SvnVcs.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testOneFileCreatedDeep() throws Exception {
    final VirtualFile versionedParent = createDirInCommand(myWorkingCopyDir, "versionedParent");
    final String name = "ign123";
    myVcs.getSvnKitManager().createWCClient().doSetProperty(new File(versionedParent.getPath()), SvnPropertyKeys.SVN_IGNORE,
                                         SVNPropertyValue.create(name + "\n"), true, SVNDepth.EMPTY, null, null);
    checkin();
    update();

    final List<VirtualFile> ignored = new ArrayList<>();
    final VirtualFile ignChild = createDirInCommand(versionedParent, name);
    ignored.add(ignChild);
    VirtualFile current = ignChild;
    for (int i = 0; i < 10; i++) {
      current = createDirInCommand(current, "dir" + i);
      ignored.add(current);
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      clManager.ensureUpToDate(false);
      Assert.assertTrue(clManager.getDefaultChangeList().getChanges().isEmpty());
    }
    testOneFile(current, "file.txt");

    final Random rnd = new Random(17);
    for (int i = 0; i < 20; i++) {
      final int idx = rnd.nextInt(ignored.size());
      testOneFile(ignored.get(idx), "file" + i + ".txt");
    }
  }

  @Test
  public void testManyDeep() throws Exception {
    final VirtualFile versionedParent = createDirInCommand(myWorkingCopyDir, "versionedParent");
    final String name = "ign123";
    final String name2 = "ign321";
    myVcs.getSvnKitManager().createWCClient().doSetProperty(new File(versionedParent.getPath()), SvnPropertyKeys.SVN_IGNORE,
                                         SVNPropertyValue.create(name + "\n" + name2 + "\n"), true, SVNDepth.EMPTY, null, null);
    checkin();
    update();

    final List<VirtualFile> ignored = new ArrayList<>();
    final VirtualFile ignChild = createDirInCommand(versionedParent, name);
    final VirtualFile ignChild2 = createDirInCommand(versionedParent, name2);
    ignored.add(ignChild);
    ignored.add(ignChild2);
    VirtualFile current = ignChild;
    for (int i = 0; i < 10; i++) {
      current = createDirInCommand(current, "dir" + i);
      ignored.add(current);
    }
    current = ignChild2;
    for (int i = 0; i < 10; i++) {
      current = createDirInCommand(current, "dir" + i);
      ignored.add(current);
    }

    final Random rnd = new Random(17);
    final List<VirtualFile> vf = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      final int idx = rnd.nextInt(ignored.size());
      vf.add(createFileInCommand(ignored.get(idx), "file" + i + ".txt", "***"));
    }
    for (int i = 0; i < 50; i++) {
      final VirtualFile virtualFile = vf.get(rnd.nextInt(vf.size()));
      testImpl(virtualFile);
    }
  }

  private void testOneFile(VirtualFile current, final String name) {
    final VirtualFile file = createFileInCommand(current, name, "123");
    testImpl(file);
  }

  private void testImpl(VirtualFile file) {
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    Assert.assertTrue(clManager.getDefaultChangeList().getChanges().isEmpty());

    VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
    clManager.ensureUpToDate(false);

    Assert.assertTrue(clManager.getDefaultChangeList().getChanges().isEmpty());
    final FileStatus status = clManager.getStatus(file);
    Assert.assertTrue(status.getText(), FileStatus.IGNORED.equals(status));
  }
}
