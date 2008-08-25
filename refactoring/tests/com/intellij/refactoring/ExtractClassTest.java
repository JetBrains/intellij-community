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
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;

import java.util.ArrayList;

public class ExtractClassTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        methods.add(aClass.findMethodsByName("foo", false)[0]);
        ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<PsiField>(), methods, new ArrayList<PsiClass>(), "", "Extracted");
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testStatic() throws Exception {
    doTest();
  }
}