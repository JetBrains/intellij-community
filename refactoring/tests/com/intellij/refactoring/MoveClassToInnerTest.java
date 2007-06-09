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
import com.intellij.usageView.UsageInfo;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class MoveClassToInnerTest extends CodeInsightTestCase {
  public void testContextChange1() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testContextChange2() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testMoveMultiple1() throws Exception {
    doTest(new String[] { "pack1.Class1", "pack1.Class2" }, "pack2.A");
  }

  public void testRefToInner() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testRefToConstructor() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack2.A");
  }

  public void testSecondaryClass() throws Exception {
    doTest(new String[] { "pack1.Class2" }, "pack1.User");
  }

  public void testStringsAndComments() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testStringsAndComments2() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testNonJava() throws Exception {
    doTest(new String[] { "pack1.Class1" }, "pack1.A");
  }

  public void testPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Field <b><code>Class1.c2</code></b> uses a package-local class <b><code>pack1.Class2</code></b>.");
  }

  public void _testMoveIntoPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "will no longer be accessible");
  }

  private void doTest(String[] classNames, String targetClassName) throws Exception{
    VirtualFile rootDir = prepareTest();

    performAction(classNames, targetClassName);

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    IdeaTestUtil.assertDirectoriesEqual(rootDir2, rootDir, MultiFileTestCase.CVS_FILE_FILTER);
  }

  private VirtualFile prepareTest() throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
    return PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
  }

  private String getRoot() {
    return PathManagerEx.getTestDataPath()+ "/refactoring/moveClassToInner/" + getTestName(true);
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) throws Exception {
    prepareTest();
    PsiClass classToMove = myPsiManager.findClass(className, myProject.getAllScope());
    PsiClass targetClass = myPsiManager.findClass(targetClassName, myProject.getAllScope());
    MoveClassToInnerProcessor processor = new MoveClassToInnerProcessor(myProject, classToMove, targetClass, true, true);
    UsageInfo[] usages = processor.findUsages();
    List<String> conflicts = processor.getConflicts(usages);
    assertSameElements(conflicts, expectedConflicts);
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