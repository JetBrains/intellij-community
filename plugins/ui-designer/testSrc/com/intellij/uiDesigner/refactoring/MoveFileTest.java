// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.PlatformTestUtil;

public class MoveFileTest extends LightMultiFileTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // Wait for all other projects to be closed to avoid VFS cross-project conflicts
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project != getProject() && !project.isDisposed()) {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project);
      }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Ensure all invokeLater tasks are processed to avoid leaking project state
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData/move/";
  }

  public void testMoveIcon() {
    doTest("to", "from/addmodulewizard.png");
  }

  //Both names are relative to root directory
  private void doTest(final String targetDirName, final String fileToMove) {
    doTest(() -> {
      final VirtualFile child = myFixture.findFileInTempDir(fileToMove);
      assertNotNull("File " + fileToMove + " not found", child);
      PsiFile file = getPsiManager().findFile(child);

      final VirtualFile child1 = myFixture.findFileInTempDir(targetDirName);
      assertNotNull("File " + targetDirName + " not found", child1);
      final PsiDirectory targetDirectory = getPsiManager().findDirectory(child1);

      new MoveFilesOrDirectoriesProcessor(getProject(), new PsiElement[]{file}, targetDirectory,
                                          false, false, null, null).run();
    });
  }
}
