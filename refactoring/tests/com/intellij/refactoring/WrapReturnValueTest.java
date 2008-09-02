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
import com.intellij.refactoring.wrapreturnvalue.WrapReturnValueProcessor;

public class WrapReturnValueTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/wrapReturnValue/";
  }

  private void doTest(final boolean existing) throws Exception {
    doTest(existing, false);
  }

  private void doTest(final boolean existing, boolean fail) throws Exception {
    try {
      doTest(new PerformAction() {
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          PsiClass aClass = myJavaFacade.findClass("Test");

          assertNotNull("Class Test not found", aClass);

          final PsiMethod method = aClass.findMethodsByName("foo", false)[0];



          final String wrapperClassName = "Wrapper";

          final PsiClass wrapperClass = myJavaFacade.findClass(wrapperClassName);

          assertTrue(!existing || wrapperClass != null);
          final PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
          WrapReturnValueProcessor processor = new WrapReturnValueProcessor(wrapperClassName, "", method, existing, delegateField);
          processor.run();
          LocalFileSystem.getInstance().refresh(false);
          FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (RuntimeException e) {
      if (fail) {
        e.printStackTrace();
        return;
      }
      if (!fail) throw e;
    }
    if (fail) {
      fail("Conflict was not found");
    }
  }

  public void testSimple() throws Exception {
    doTest(false);
  }

  public void testGenerics() throws Exception {
    doTest(false);
  }

  public void testInconsistentWrapper() throws Exception {
    doTest(true, true);
  }

  public void testWrapper() throws Exception {
    doTest(true);
  }

  public void testStrip() throws Exception {
    doTest(true);
  }

  public void testNoConstructor() throws Exception {
    doTest(true, true);
  }
}