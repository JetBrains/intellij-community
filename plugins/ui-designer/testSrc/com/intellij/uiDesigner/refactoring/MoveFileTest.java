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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import org.jetbrains.annotations.NotNull;

public class MoveFileTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/move/";
  }

  public void testMoveIcon() {
    doTest("to", "from/addmodulewizard.png");
  }

  //Both names are relative to root directory
  private void doTest(final String targetDirName, final String fileToMove) {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) {
        final VirtualFile child = rootDir.findFileByRelativePath(fileToMove);
        assertNotNull("File " + fileToMove + " not found", child);
        PsiFile file = myPsiManager.findFile(child);

        final VirtualFile child1 = rootDir.findChild(targetDirName);
        assertNotNull("File " + targetDirName + " not found", child1);
        final PsiDirectory targetDirectory = myPsiManager.findDirectory(child1);

        new MoveFilesOrDirectoriesProcessor(myProject, new PsiElement[] {file}, targetDirectory,
                                            false, false, null, null).run();
        /*assert targetDirectory != null;
        final PsiFile psiFile = targetDirectory.findFile(fileToMove);
        assert psiFile != null;
        final Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
        assert document != null;
        PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(document);*/
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }
}
