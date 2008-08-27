/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanProcessor;

public class RemoveMiddleManTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/removemiddleman/";
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean delete) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final PsiField field = aClass.findFieldByName("myField", false);
        RemoveMiddlemanProcessor processor = new RemoveMiddlemanProcessor(field, delete);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testNoGetter() throws Exception {
    doTest();
  }

  public void testSiblings() throws Exception {
    doTest();
  }

}