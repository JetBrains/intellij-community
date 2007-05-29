package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class InlineToAnonymousClassTest extends LightCodeInsightTestCase {
  public void testSimple() throws Exception {
    doTest();
  }
  
  public void testChangeToSuperType() throws Exception {
    doTest();
  }

  public void testImplementsInterface() throws Exception {
    doTest();
  }

  public void testClassInitializer() throws Exception {
    doTest();
  }

  public void testConstructor() throws Exception {
    doTest();
  }

  public void testConstructorWithArguments() throws Exception {
    doTest();
  }

  public void testConstructorWithArgumentsInExpression() throws Exception {
    doTest();
  }

  public void testMultipleConstructors() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    performAction(false);
    checkResultByFile(null, fileName + ".after", true);
  }

  private void performAction(final boolean inlineThisOnly) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);
    final InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(getProject(), (PsiClass) element, inlineThisOnly);
    processor.run();
  }
}