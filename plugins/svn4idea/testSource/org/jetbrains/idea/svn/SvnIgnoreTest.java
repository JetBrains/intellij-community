// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.jetbrains.idea.svn.SvnPropertyKeys.SVN_IGNORE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SvnIgnoreTest extends SvnTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testOneFileCreatedDeep() throws Exception {
    final VirtualFile versionedParent = createDirInCommand(myWorkingCopyDir, "versionedParent");
    final String name = "ign123";
    File file = virtualToIoFile(versionedParent);
    vcs.getFactory(file).createPropertyClient().setProperty(file, SVN_IGNORE, PropertyValue.create(name + "\n"), Depth.EMPTY, true);
    checkin();
    update();

    final List<VirtualFile> ignored = new ArrayList<>();
    final VirtualFile ignChild = createDirInCommand(versionedParent, name);
    ignored.add(ignChild);
    VirtualFile current = ignChild;
    for (int i = 0; i < 10; i++) {
      current = createDirInCommand(current, "dir" + i);
      ignored.add(current);
      refreshChanges();
      assertNoChanges();
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
    File file = virtualToIoFile(versionedParent);
    vcs.getFactory().createPropertyClient()
       .setProperty(file, SVN_IGNORE, PropertyValue.create(name + "\n" + name2 + "\n"), Depth.EMPTY, true);
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
    refreshChanges();
    assertNoChanges();

    dirtyScopeManager.fileDirty(file);
    changeListManager.ensureUpToDate();

    assertNoChanges();
    final FileStatus status = changeListManager.getStatus(file);
    assertEquals(FileStatus.IGNORED, status);
  }

  private void assertNoChanges() {
    assertThat(changeListManager.getDefaultChangeList().getChanges(), is(empty()));
  }
}
