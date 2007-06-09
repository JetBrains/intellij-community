package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 * @author yole
 */
public class MoveClassToInnerTest extends CodeInsightTestCase {
  public void testContextChange1() throws Exception {
    doTest("contextChange1", new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testContextChange2() throws Exception {
    doTest("contextChange2", new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testMoveMultiple() throws Exception {
    doTest("moveMultiple1", new String[] { "pack1.Class1", "pack1.Class2" }, "pack2.A");
  }

  private void doTest(String testName, String[] classNames, String targetClassName) throws Exception{
    String root = PathManagerEx.getTestDataPath()+ "/refactoring/moveClassToInner/" + testName;

    String rootBefore = root + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);

    performAction(classNames, targetClassName);

    String rootAfter = root + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, MultiFileTestCase.CVS_FILE_FILTER);
  }

  private void performAction(String[] classNames, String targetClassName) throws Exception{
    final PsiClass[] classes = new PsiClass[classNames.length];
    for(int i = 0; i < classes.length; i++){
      String className = classNames[i];
      classes[i] = myPsiManager.findClass(className, myProject.getAllScope());
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiClass targetClass = myPsiManager.findClass(targetClassName, myProject.getAllScope());
    assertNotNull(targetClass);

    for(PsiClass psiClass: classes) {
      new MoveClassToInnerProcessor(myProject, psiClass, targetClass, true, true).run();
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}