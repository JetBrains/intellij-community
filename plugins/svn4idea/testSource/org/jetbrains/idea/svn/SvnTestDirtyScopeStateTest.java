// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeVfsListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class SvnTestDirtyScopeStateTest extends SvnTestCase {
  @Override
  public void setUp() throws Exception {
    myInitChangeListManager = false;
    super.setUp();

    VcsDirtyScopeVfsListener.getInstance(myProject).setForbid(true);
  }

  @Test
  public void testWhatIsDirty() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VcsDirtyScopeManagerImpl vcsDirtyScopeManager = (VcsDirtyScopeManagerImpl) VcsDirtyScopeManager.getInstance(myProject);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final VirtualFile fileC = createFileInCommand("c.txt", "old content");
    final VirtualFile fileD = createFileInCommand("d.txt", "old content");
    waitABit();

    final List<FilePath> list = ObjectsConvertor.vf2fp(Arrays.asList(file, fileB, fileC, fileD));

    vcsDirtyScopeManager.retrieveScopes();
    vcsDirtyScopeManager.changesProcessed();

    vcsDirtyScopeManager.fileDirty(file);
    vcsDirtyScopeManager.fileDirty(fileB);

    final Collection<FilePath> dirty1 = vcsDirtyScopeManager.whatFilesDirty(list);
    assertTrue(dirty1.contains(VcsUtil.getFilePath(file)));
    assertTrue(dirty1.contains(VcsUtil.getFilePath(fileB)));

    assertTrue(!dirty1.contains(VcsUtil.getFilePath(fileC)));
    assertTrue(!dirty1.contains(VcsUtil.getFilePath(fileD)));

    vcsDirtyScopeManager.retrieveScopes();

    final Collection<FilePath> dirty2 = vcsDirtyScopeManager.whatFilesDirty(list);
    assertTrue(dirty2.contains(VcsUtil.getFilePath(file)));
    assertTrue(dirty2.contains(VcsUtil.getFilePath(fileB)));

    assertTrue(!dirty2.contains(VcsUtil.getFilePath(fileC)));
    assertTrue(!dirty2.contains(VcsUtil.getFilePath(fileD)));

    vcsDirtyScopeManager.changesProcessed();

    final Collection<FilePath> dirty3 = vcsDirtyScopeManager.whatFilesDirty(list);
    assertTrue(!dirty3.contains(VcsUtil.getFilePath(file)));
    assertTrue(!dirty3.contains(VcsUtil.getFilePath(fileB)));

    assertTrue(!dirty3.contains(VcsUtil.getFilePath(fileC)));
    assertTrue(!dirty3.contains(VcsUtil.getFilePath(fileD)));
  }

  private void waitABit() {
    TimeoutUtil.sleep(100);
  }

  @Test
  public void testOkToAddScopeUnderWriteAction() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VcsDirtyScopeManagerImpl vcsDirtyScopeManager = (VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(myProject);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final VirtualFile fileC = createFileInCommand("c.txt", "old content");
    final VirtualFile fileD = createFileInCommand("d.txt", "old content");
    waitABit();

    final List<FilePath> list = ObjectsConvertor.vf2fp(Arrays.asList(file, fileB, fileC, fileD));

    vcsDirtyScopeManager.retrieveScopes();
    vcsDirtyScopeManager.changesProcessed();

    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      vcsDirtyScopeManager.fileDirty(file);
      vcsDirtyScopeManager.fileDirty(fileB);
    });


    final FilePath fp = VcsUtil.getFilePath(file);
    final FilePath fpB = VcsUtil.getFilePath(fileB);
    final long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < (start + 3000)) {
      synchronized (this) {
        try {
          wait(50);
        }
        catch (InterruptedException e) {
          //
        }
      }
      final Collection<FilePath> dirty1 = vcsDirtyScopeManager.whatFilesDirty(list);
      if (dirty1.contains(fp) && dirty1.contains(fpB)) return;
    }
    assertTrue(false);
  }
}
