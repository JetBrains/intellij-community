package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.refactoring.rename.RenameProcessor;

public class RenameClassTest extends MultiFileTestCase {
  public void testNonJava() throws Exception {
    doTest("pack1.Class1", "Class1New");
  }

  public void testJsp() throws Exception {
    doTest("pack1.TestClass", "NewTestClass");
  }

  public void testCollision() throws Exception {
    doTest("pack1.MyList", "List");
  }

  public void testInnerClass() throws Exception {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() throws Exception {
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testConstructorJavadoc() throws Exception {
    doTest("Test", "Test1");
  }

  public void testCollision1() throws Exception {
    doTest("Loader", "Reader");
  }

  private void doTest(final String qClassName, final String newName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        RenameClassTest.this.performAction(rootDir, qClassName, newName);
      }
    });
  }


  private void performAction(VirtualFile rootDir, String qClassName, String newName) throws Exception {
    PsiClass aClass = myPsiManager.findClass(qClassName);
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(myProject, aClass, newName, true, true).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  protected String getTestRoot() {
    return "/refactoring/renameClass/";
  }
}
