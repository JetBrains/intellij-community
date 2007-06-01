package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.util.Ref;
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

  public void testMethodUsage() throws Exception {
    doTest();
  }

  public void testConstructorArgumentToField() throws Exception {
    doTest();
  }

  public void testField() throws Exception {
    doTest();
  }

  public void testStaticConstantField() throws Exception {
    doTest();
  }

  public void testWritableInitializedField() throws Exception {
    doTest();
  }

  public void testNullInitializedField() throws Exception {
    doTest();
  }

  public void testInnerClass() throws Exception {
    doTest();
  }

  public void testConstructorToInstanceInitializer() throws Exception {
    doTest();
  }

  public void testNewExpressionContext() throws Exception {
    doTest();
  }

  public void testWritableFieldInitializedWithParameter() throws Exception {
    doTest();
  }

  public void testNoInlineAbstract() throws Exception {
    doTestNoInline("Abstract classes cannot be inlined");
  }

  public void testNoInlineWithSubclasses() throws Exception {
    doTestNoInline("Classes which have subclasses cannot be inlined");
  }

  public void testNoInlineMultipleInterfaces() throws Exception {
    doTestNoInline("Classes which implement multiple interfaces cannot be inlined");
  }

  public void testNoInlineSuperclassInterface() throws Exception {
    doTestNoInline("Classes which have a superclass and implement an interface cannot be inlined");
  }

  public void testNoInlineMethodUsage() throws Exception {
    doTestNoInline("Class cannot be inlined because it has usages of methods not inherited from its superclass or interface");
  }

  public void testNoInlineFieldUsage() throws Exception {
    doTestNoInline("Class cannot be inlined because it has usages of fields not inherited from its superclass");
  }

  public void testNoInlineStaticField() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static fields with non-constant initializers");
  }

  public void testNoInlineStaticInitializer() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static initializers");
  }

  public void testNoInlineClassLiteral() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because it has usages of its class literal");
  }

  public void testNoInlineUnresolvedConstructor() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineUnresolvedConstructor2() throws Exception {
    doTestPreprocessUsages("Class cannot be inlined because a call to its constructor is unresolved");
  }

  public void testNoInlineStaticInnerClass() throws Exception {
    doTestNoInline("Class cannot be inlined because it has static inner classes");
  }

  private void doTestNoInline(final String expectedMessage) throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    String message = InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element);
    assertEquals(expectedMessage, message);
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    performAction(false);
    checkResultByFile(null, fileName + ".after", true);
  }

  private void doTestPreprocessUsages(final String expectedMessage) throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineToAnonymousClass/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                                                             TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);

    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element));
    final InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(getProject(), (PsiClass) element, false);
    Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>(processor.findUsages());
    String message = processor.getPreprocessUsagesMessage(refUsages);
    assertEquals(expectedMessage, message);
  }

  private void performAction(final boolean inlineThisOnly) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertInstanceOf(element, PsiClass.class);
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage((PsiClass) element));
    final InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(getProject(), (PsiClass) element, inlineThisOnly);
    processor.run();
  }
}
