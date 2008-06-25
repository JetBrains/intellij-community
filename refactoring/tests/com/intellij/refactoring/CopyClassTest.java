package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.copy.CopyClassesHandler;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public class CopyClassTest extends CodeInsightTestCase {
  public void testReplaceAllOccurrences() throws Exception {
    doTest("Foo", "Bar");
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    String root = PathManagerEx.getTestDataPath()+ "/refactoring/copyClass/" + getTestName(true);

    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    performAction(oldName, copyName);

    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile fileAfter = rootDir.findChild(copyName + ".java");
    VirtualFile fileExpected = rootDir.findChild(copyName + ".java.expected");

    IdeaTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private void performAction(final String oldName, final String copyName) throws IncorrectOperationException {
    PsiClass oldClass = JavaPsiFacade.getInstance(myProject).findClass(oldName, ProjectScope.getAllScope(myProject));
    CopyClassesHandler.doCopyClass(oldClass, copyName, oldClass.getContainingFile().getContainingDirectory());
  }
}
