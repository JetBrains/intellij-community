// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

/**
 * @author yole
 */
public class SvnAddTest extends SvnTestCase {
  @Test
  public void testCopy() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    checkin();
    copyFileInCommand(file, "b.txt");
    runAndVerifyStatusSorted("A + b.txt");
  }

  // IDEADEV-16268
  @Test
  public void testDirAndFileInCommand() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    writeCommandAction(myProject).run(() -> {
      VirtualFile dir = myWorkingCopyDir.createChildDirectory(this, "child");
      dir.createChildData(this, "a.txt");
    });

    runAndVerifyStatusSorted("A child", "A child/a.txt");
  }

  // IDEADEV-19308
  @Test
  public void testDirAfterFile() throws Exception {
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "dir");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "content");

    runAndVerifyStatusSorted("? dir");

    final List<VirtualFile> files = new ArrayList<>();
    files.add(file);
    files.add(dir);
    final List<VcsException> errors = vcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
    Assert.assertEquals(0, errors.size());
  }

  @Test
  public void testUndoAdd() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    createFileInCommand("a.txt", "old content");
    checkin();
    undo();
    runAndVerifyStatusSorted("D a.txt");
    Assert.assertFalse(new File(myWorkingCopyDir.getPath(), "a.txt").exists());
  }
}
