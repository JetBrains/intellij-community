package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;

public class TurnRefsToSuperTest extends MultiFileTestCase {


  public void testSuperClass() throws Exception {
    doTest("AClass", "ASuper", true);
  }

  public void testMethodFromSuper() throws Exception {
    doTest("AClass", "ASuper", true);
  }

  public void testRemoveImport() throws Exception {
    doTest("pack1.AClass", "pack1.AnInterface", true);
  }

  public void testToArray() throws Exception {
    doTest("A", "I", true);
  }


  public void testArrayElementAssignment() throws Exception {
    doTest("C", "I", true);
  }

  public void testReturnValue() throws Exception {
    doTest("A", "I", true);
  }

  public void testReturnValue2() throws Exception {
    doTest("A", "I", true);
  }

  public void testCast() throws Exception {
    doTest("A", "I", true);
  }


  public void testUseAsArg() throws Exception {
    doTest("AClass", "I", true);
  }

  public void testClassUsage() throws Exception {
    doTest("A", "I", true);
  }

  public void testInstanceOf() throws Exception {
    doTest("A", "I", false);
  }

  public void testFieldTest() throws Exception {
    doTest("Component1", "IDoSomething", false);
  }

  public void testScr34000() throws Exception {
    doTest("SimpleModel", "Model", false);
  }

  public void testScr34020() throws Exception {
    doTest("java.util.List", "java.util.Collection", false);
  }

   public void testCommonInheritor() throws Exception {
    doTest("Client.V", "Client.L", false);
  }

  public void testCommonInheritorFail() throws Exception {
    doTest("Client.V", "Client.L", false);
  }

  public void testCommonInheritorResults() throws Exception {
    doTest("Client.V", "Client.L", false);
  }

  public void testCommonInheritorResultsFail() throws Exception {
    doTest("Client.V", "Client.L", false);
  }

  public void testCommonInheritorResultsFail2() throws Exception {
    doTest("Client.V", "Client.L", false);
  }


  public void testIDEA6505() throws Exception {
    doTest("Impl", "IB", false);
  }

  public void testIDEADEV5517() throws Exception {
    doTest("Xyz", "Xint", false);
  }

  public void testIDEADEV5517Noop() throws Exception {
    doTest("Xyz", "Xint", false);
  }

  public void testIDEADEV6136() throws Exception {
    doTest("A", "B", false);
  }

  private void doTest(final String className, final String superClassName, final boolean replaceInstanceOf) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        TurnRefsToSuperTest.this.performAction(className, superClassName, replaceInstanceOf);
      }
    });
  }

  public String getTestRoot() {
    return "/refactoring/turnRefsToSuper/";
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) throws Exception {
    final PsiClass aClass = myPsiManager.findClass(className);
    assertNotNull("Class " + className + " not found", aClass);
    PsiClass superClass = myPsiManager.findClass(superClassName);
    assertNotNull("Class " + superClassName + " not found", superClass);

    new TurnRefsToSuperProcessor(myProject, aClass, superClass, replaceInstanceOf).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}