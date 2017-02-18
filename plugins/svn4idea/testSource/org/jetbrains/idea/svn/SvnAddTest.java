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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SvnAddTest extends Svn17TestCase {
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
    new WriteCommandAction.Simple(myProject) {
      @Override
      public void run() {
        try {
          VirtualFile dir = myWorkingCopyDir.createChildDirectory(this, "child");
          dir.createChildData(this, "a.txt");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    
    runAndVerifyStatusSorted("A child", "A child" + File.separatorChar + "a.txt");
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
    final List<VcsException> errors = SvnVcs.getInstance(myProject).getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
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
