package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformatingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;

/**
 *  @author dsl
 */
public class MoveInnerTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/moveInner/";
  }

  public void testScr13730() throws Exception {
    doTest(createAction("pack1.TopLevel.StaticInner", "StaticInner", false, null, false, false));
  }

  public void testScr15142() throws Exception {
    doTest(createAction("xxx.Outer.Inner", "Inner", false, null, false, false));
  }

  public void testNonJavaFiles() throws Exception {
    doTest(createAction("pack1.Outer.Inner", "Inner", false, null, true, true));
  }

  public void testScr22592() throws Exception {
    doTest(createAction("xxx.Outer.Inner", "Inner", true, "outer", false, false));
  }

  /*
  public void testScr30106() throws Exception {
    doTest(createAction("p.A.B", "B", true, "outer", false, false));
  }
  */

  private PerformAction createAction(final String innerClassName,
                                     final String newClassName,
                                     final boolean passOuterClass,
                                     final String parameterName,
                                     final boolean searchInComments,
                                     final boolean searchInNonJava) {
    return new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final PsiManager manager = PsiManager.getInstance(myProject);
        final PsiClass aClass = manager.findClass(innerClassName, GlobalSearchScope.moduleScope(myModule));
        final MoveInnerProcessor moveInnerProcessor = new MoveInnerProcessor(myProject, null);
        final PsiElement targetContainer = MoveInnerImpl.getTargetContainer(aClass, false);
        assertNotNull(targetContainer);
        moveInnerProcessor.setup(aClass, newClassName, passOuterClass, parameterName,
                                 searchInComments, searchInNonJava, targetContainer);
        moveInnerProcessor.run();
        PostprocessReformatingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }

}
