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
  private VirtualFile myRootDir;

  public void testReplaceAllOccurrences() throws Exception {
    doTest("Foo", "Bar");
  }

  public void testLibraryClass() throws Exception {  // IDEADEV-28791
    doTest("java.util.ArrayList", "Bar");
  }

  private void doTest(final String oldName, final String copyName) throws Exception {
    String root = PathManagerEx.getTestDataPath()+ "/refactoring/copyClass/" + getTestName(true);

    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk15("java 1.5"));
    myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    performAction(oldName, copyName);

    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile fileAfter = myRootDir.findChild(copyName + ".java");
    VirtualFile fileExpected = myRootDir.findChild(copyName + ".java.expected");

    IdeaTestUtil.assertFilesEqual(fileExpected, fileAfter);
  }

  private void performAction(final String oldName, final String copyName) throws IncorrectOperationException {
    PsiClass oldClass = JavaPsiFacade.getInstance(myProject).findClass(oldName, ProjectScope.getAllScope(myProject));
    CopyClassesHandler.doCopyClass(oldClass, copyName, myPsiManager.findDirectory(myRootDir));
  }
}
