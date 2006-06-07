package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryTest extends CodeInsightTestCase {
  private LanguageLevel myPrevLanguageLevel;

  public void testEmptyConstructor() throws Exception { runTest("01", null); }

  public void testSubclass() throws Exception { runTest("02", null); }

  public void testDefaultConstructor() throws Exception { runTest("03", null); }

  public void testInnerClass() throws Exception { runTest("04", "OuterClass"); }

  public void testSubclassVisibility() throws Exception { runTest("05", null); }

  public void testImplicitConstructorUsages() throws Exception { runTest("06", null); }

  public void testImplicitConstructorCreation() throws Exception { runTest("07", null); }

  public void testConstructorTypeParameters() throws Exception { runTest("08", null); }

  private void runTest(final String testIndex, String targetClassName) throws Exception {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testIndex + ".java");
    perform(targetClassName);
    checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testIndex + ".java");
  }


  private void perform(String targetClassName) throws Exception {
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = myFile.findElementAt(offset);
    PsiMethod constructor = null;
    PsiClass aClass = null;
    while (true) {
      if (element == null || element instanceof PsiFile) {
        assertTrue(false);
        return;
      }

      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        constructor = (PsiMethod)element;
        break;
      }

      if (element instanceof PsiClass && ((PsiClass)element).getConstructors().length == 0) {
        aClass = (PsiClass)element;
        break;
      }
      element = element.getParent();
    }
    final ReplaceConstructorWithFactoryProcessor replaceConstructorWithFactoryProcessor;
    PsiClass targetClass = null;
    if (targetClassName != null) {
      targetClass = myPsiManager.findClass(targetClassName);
      assertTrue(targetClass != null);
    }

    if (constructor != null) {
      if (targetClass == null) {
        targetClass = constructor.getContainingClass();
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        myProject, constructor, constructor.getContainingClass(), targetClass, "new" + constructor.getName());
    }
    else {
      if (targetClass == null) {
        targetClass = aClass;
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        myProject, null, aClass, targetClass, "new" + aClass.getName());
    }
    replaceConstructorWithFactoryProcessor.run();
  }


  protected void setUp() throws Exception {
    super.setUp();
    myPrevLanguageLevel = getPsiManager().getEffectiveLanguageLevel();
    getPsiManager().setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }


  protected void tearDown() throws Exception {
    getPsiManager().setEffectiveLanguageLevel(myPrevLanguageLevel);
    super.tearDown();
  }
}
