package com.intellij.uiDesigner.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;

public class MoveFileTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }

  protected String getTestRoot() {
    return "/move/";
  }

  public void testMoveIcon() throws Exception {
    doTest("to", "from/addmodulewizard.png");
  }

  //Both names are relative to root directory
  private void doTest(final String targetDirName, final String fileToMove) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
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
