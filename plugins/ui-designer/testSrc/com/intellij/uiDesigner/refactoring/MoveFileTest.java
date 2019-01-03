/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.uiDesigner.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;

public class MoveFileTest extends LightMultiFileTestCase {
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
