/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.introduceparameterobject.IntroduceParameterObjectProcessor;

import java.util.Arrays;

public class IntroduceParameterObjectTest extends MultiFileTestCase{
  protected String getTestRoot() {
    return "/refactoring/introduceParameterObject/";
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("Test");

        assertNotNull("Class Test not found", aClass);

        final PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        IntroduceParameterObjectProcessor processor = new IntroduceParameterObjectProcessor("Param", "", method,
                                                                                            Arrays.asList(method.getParameterList().getParameters()),
                                                                                            null, false, false, false);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  public void testPrimitive() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testIncrement() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

}