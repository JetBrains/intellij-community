// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MoveFileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, PluginPathManager.getPluginHomePath("ui-designer") + "/testData/move/");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      // Wait for all other projects to be closed to avoid VFS cross-project conflicts
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        if (project != myFixture.getProject() && !project.isDisposed()) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(project);
        }
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
  }

  @AfterEach
  void tearDown() {
    // Ensure all invokeLater tasks are processed to avoid leaking project state
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
  }

  @Test
  public void testMoveIcon() {
    doTest("to", "from/addmodulewizard.png");
  }

  //Both names are relative to root directory
  private void doTest(final String targetDirName, final String fileToMove) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      final VirtualFile child = myFixture.findFileInTempDir(fileToMove);
      assertNotNull(child, "File " + fileToMove + " not found");
      PsiFile file = PsiManager.getInstance(myFixture.getProject()).findFile(child);

      final VirtualFile child1 = myFixture.findFileInTempDir(targetDirName);
      assertNotNull(child1, "File " + targetDirName + " not found");
      final PsiDirectory targetDirectory = PsiManager.getInstance(myFixture.getProject()).findDirectory(child1);

      new MoveFilesOrDirectoriesProcessor(myFixture.getProject(), new PsiElement[]{file}, targetDirectory,
                                          false, false, null, null).run();
    });
  }
}
