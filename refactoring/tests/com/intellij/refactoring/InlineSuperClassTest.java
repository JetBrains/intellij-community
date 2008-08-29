/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.inlineSuperClass.InlineSuperClassRefactoringProcessor;

public class InlineSuperClassTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/inlineSuperClass/";
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean fail) throws Exception {
    try {
      doTest(new PerformAction() {
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          PsiClass aClass = myJavaFacade.findClass("Test");

          if (aClass == null) aClass = myJavaFacade.findClass("p.Test");
          assertNotNull("Class Test not found", aClass);

          PsiClass superClass = myJavaFacade.findClass("Super");

          if (superClass == null) superClass = myJavaFacade.findClass("p1.Super");
          assertNotNull("Class Super not found", superClass);

          new InlineSuperClassRefactoringProcessor(getProject(), superClass, aClass).run();

          //LocalFileSystem.getInstance().refresh(false);
          //FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      if (fail) {
        return;
      }
      else {
        throw e;
      }
    }
    if (fail) {
      fail("Conflict was not detected");
    }
  }


  public void testSimple() throws Exception {
    doTest();
  }

  public void testSimpleGenerics() throws Exception {
    doTest();
  }

  public void testConflictGenerics() throws Exception {
    doTest(true);
  }

  public void testImports() throws Exception {
    doTest();
  }

  public void testGenerics() throws Exception {
    doTest();
  }

  public void testVisibility() throws Exception {
    doTest();
  }

  public void testNewexpr() throws Exception {
    doTest();
  }

  public void testConflictConstructors() throws Exception {
    doTest(true);
  }

  public void testConflictMultipleConstructors() throws Exception {
    doTest(true);
  }

  public void testMultipleConstructors() throws Exception {
    doTest();
  }
}