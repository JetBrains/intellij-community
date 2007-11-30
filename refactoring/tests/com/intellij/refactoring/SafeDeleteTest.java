package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.idea.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class SafeDeleteTest extends MultiFileTestCase {
  private VirtualFile myRootBefore;
  
  public void testImplicitCtrCall() throws Exception {
    try {
      doTest("Super");
      fail();
    }
    catch (RuntimeException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }
  public void testImplicitCtrCall2() throws Exception {
    try {
      doTest("Super");
      fail();
    }
    catch (RuntimeException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testMultipleInterfacesImplementation() throws Exception {
    myDoCompare = false;
    doTest("IFoo");
  }

  private void doTest(@NonNls final String qClassName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        SafeDeleteTest.this.performAction(qClassName);
        IdeaTestUtil.assertDirectoriesEqual(rootAfter, myRootBefore, CVS_FILE_FILTER);
      }
    });
  }

  private void performAction(String qClassName) throws Exception {
    PsiClass aClass = myPsiManager.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);

    String root = ProjectRootManager.getInstance(getProject()).getContentRoots()[0].getPath();
    myRootBefore = configureByFiles(new File(root), new VirtualFile[]{aClass.getContainingFile().getVirtualFile()});
    final PsiElement psiElement = TargetElementUtil
          .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{psiElement}, false);
  }

  protected String getTestRoot() {
    return "/refactoring/safeDelete/";
  }
}