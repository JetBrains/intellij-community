/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class SvnCommitTest extends Svn17TestCase {
  private SvnVcs myVcs;
  private VcsDirtyScopeManager myDirtyScopeManager;
  private ChangeListManager myChangeListManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myVcs = SvnVcs.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Test
  public void testSimpleCommit() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    run2variants(new MyRunner() {
      private String myName = "a.txt";

      @Override
      protected void run() {
        final VirtualFile file = createFileInCommand(myWorkingCopyDir, myName, "123");
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFile(file, FileStatus.ADDED);
      }

      @Override
      protected void cleanup() {
        myName = "b.txt";
      }
    });
  }

  @Test
  public void testCommitRename() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    run2variants(new MyRunner() {
      private String myName = "a.txt";
      private String myRenamedName = "aRenamed.txt";

      @Override
      protected void run() {
        final VirtualFile file = createFileInCommand(myWorkingCopyDir, myName, "123");
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFile(file, FileStatus.ADDED);

        renameFileInCommand(file, myRenamedName);
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFile(file, FileStatus.MODIFIED);
      }

      @Override
      protected void cleanup() {
        myName = "b.txt";
        myRenamedName = "bRenamed.txt";
      }
    });
  }

  @Test
  public void testRenameReplace() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    run2variants(new MyRunner() {
      private String myName = "a.txt";
      private String myName2 = "aRenamed.txt";

      @Override
      protected void run() {
        final VirtualFile file = createFileInCommand(myWorkingCopyDir, myName, "123");
        final VirtualFile file2 = createFileInCommand(myWorkingCopyDir, myName2, "1235");
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(file, file2);

        renameFileInCommand(file, file.getName() + "7.txt");
        renameFileInCommand(file2, myName);

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(file, file2);
      }

      @Override
      protected void cleanup() {
        myName = "b.txt";
        myName2 = "bRenamed.txt";
      }
    });
  }

  @Test
  public void testRenameFolder() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    run2variants(new MyRunner() {
      private String folder = "f";

      @Override
      protected void run() {
        final VirtualFile dir = createDirInCommand(myWorkingCopyDir, folder);
        final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
        final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(dir, file, file2);

        renameFileInCommand(dir, dir.getName() + "dd");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(dir, file, file2);
      }

      @Override
      protected void cleanup() {
        folder = "f1";
      }
    });
  }

  @Test
  public void testCommitDeletion() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    run2variants(new MyRunner() {
      private String folder = "f";

      @Override
      protected void run() {
        final VirtualFile dir = createDirInCommand(myWorkingCopyDir, folder);
        final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
        final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(dir, file, file2);

        final FilePath dirPath = VcsUtil.getFilePath(dir.getPath(), true);
        deleteFileInCommand(dir);

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinPaths(dirPath);
      }

      @Override
      protected void cleanup() {
        folder = "f1";
      }
    });
  }

  @Test
  public void testSameRepoPlusInnerCopyCommitNative() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(false);
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        final HashSet<String> strings = checkinFiles(vf1, vf2);
        System.out.println("" + StringUtil.join(strings, "\n"));
        Assert.assertEquals(1, strings.size());
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(true);
    runner.run();
  }

  @Test
  public void testSameRepoPlusInnerCopyCommitSvnkit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(false);
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        final HashSet<String> strings = checkinFiles(vf1, vf2);
        System.out.println("" + StringUtil.join(strings, "\n"));
        Assert.assertEquals(1, strings.size());
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(true);
    runner.run();
  }

  @Test
  public void testAnotherRepoPlusInnerCopyCommitNative() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(true);
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(vf1, vf2);
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(true);
    runner.run();
  }

  @Test
  public void testAnotherRepoPlusInnerCopyCommitSvnkit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(true);
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(vf1, vf2);
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(true);
    runner.run();
  }

  @Test
  public void testPlusExternalCopyCommitNative() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareExternal();
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/external/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(vf1, vf2);
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(true);
    runner.run();
  }

  @Test
  public void testPlusExternalCopyCommitSvnkit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareExternal();
    final MyRunner runner = new MyRunner() {
      @Override
      protected void run() {
        final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
        final File fileInner = new File(myWorkingCopyDir.getPath(), "source/external/t11.txt");

        Assert.assertTrue(file1.exists());
        Assert.assertTrue(fileInner.exists());
        final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
        final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
        Assert.assertNotNull(vf1);
        Assert.assertNotNull(vf2);

        editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
        editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

        myDirtyScopeManager.markEverythingDirty();
        myChangeListManager.ensureUpToDate(false);

        checkinFiles(vf1, vf2);
      }

      @Override
      protected void cleanup() {
      }
    };
    setNativeAcceleration(false);
    runner.run();
  }

  private void checkinPaths(FilePath... files) {
    final List<Change> changes = new ArrayList<>();
    for (FilePath file : files) {
      final Change change = myChangeListManager.getChange(file);
      Assert.assertNotNull(change);
      changes.add(change);
    }
    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().commit(changes, "test comment list");
    Assert.assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    for (FilePath file : files) {
      final Change changeA = myChangeListManager.getChange(file);
      Assert.assertNull(changeA);
    }
  }

  private HashSet<String> checkinFiles(VirtualFile... files) {
    final List<Change> changes = new ArrayList<>();
    for (VirtualFile file : files) {
      final Change change = myChangeListManager.getChange(file);
      Assert.assertNotNull(change);
      changes.add(change);
    }
    final HashSet<String> feedback = new HashSet<>();
    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().commit(changes, "test comment list", o -> null, feedback);
    if (exceptions !=null && ! exceptions.isEmpty()) {
      exceptions.get(0).printStackTrace();
    }
    Assert.assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    for (VirtualFile file : files) {
      final Change changeA = myChangeListManager.getChange(file);
      Assert.assertNull(changeA);
    }
    return feedback;
  }

  protected void checkinFile(VirtualFile file, FileStatus status) {
    final Change change = myChangeListManager.getChange(file);
    Assert.assertNotNull(change);
    Assert.assertEquals(status, change.getFileStatus());
    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().commit(Collections.singletonList(change), "test comment");
    Assert.assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    final Change changeA = myChangeListManager.getChange(file);
    Assert.assertNull(changeA);
  }

  protected void run2variants(final MyRunner runner) {
    // TODO: Change this to run different variants separately. See SvnTestCase.myUseAcceleration.
    setNativeAcceleration(false);
    runner.run();
    runner.cleanup();
    setNativeAcceleration(true);
    runner.run();
  }

  private static abstract class MyRunner {
    protected abstract void run();
    protected abstract void cleanup();
  }
}
