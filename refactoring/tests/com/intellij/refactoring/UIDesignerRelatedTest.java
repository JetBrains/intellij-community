package com.intellij.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.rename.RenameProcessor;

/**
 * @author ven
 */
public class UIDesignerRelatedTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/renameUIRelated/";
  }

  public void testRenameBoundField() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myPsiManager.findClass("UIClass");
        assertNotNull(aClass);
        final PsiField field = aClass.findFieldByName("UIField", false);
        assertNotNull(field);

        new RenameProcessor(myProject, field, "OtherName", true, true).run();
      }
    });
  }

}
