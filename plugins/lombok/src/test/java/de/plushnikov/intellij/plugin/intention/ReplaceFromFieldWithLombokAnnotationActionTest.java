package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * @author Lekanich
 */
public class ReplaceFromFieldWithLombokAnnotationActionTest extends LombokIntentionActionTest {
  private Set<String> expectedAnnotations = Collections.emptySet();

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/replaceLombok";
  }

  @Override
  public IntentionAction getIntentionAction() {
    return new ReplaceWithLombokAnnotationAction();
  }

  @Override
  public boolean wasInvocationSuccessful() {
    PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PsiField field = Optional.ofNullable(PsiTreeUtil.getParentOfType(elementAtCaret, PsiField.class))
      .orElseGet(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiField.class));
    if (field == null) {
      return false;
    }

    return expectedAnnotations.stream().allMatch(field::hasAnnotation) && field.getAnnotations().length == expectedAnnotations.size();
  }

  public void testReplaceGetterFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    doTest();
  }

  public void testReplaceSetterFromField() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    doTest();
  }

  public void testReplaceAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    doTest();
  }

  public void testNotReplaceIncorrectAccessors() {
    setExpectedAnnotations();
    doTest(false);
  }

  public void testNotReplaceSetterWithAdditionalCode() {
    setExpectedAnnotations();
    doTest(false);
  }

  public void testReplaceGetterFromMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    doTest();
  }

  public void testReplaceSetterFromMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    doTest();
  }

  public void testReplaceGetterFromMethod2() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    doTest();
  }

  public void testReplaceSetterFromMethod2() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    doTest();
  }

  public void testReplaceGetterFromFieldNotCompleteMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    doTest();
  }

  public void testReplaceSetterFromFieldNotCompleteMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    doTest();
  }

  public void testNotReplaceAbstractGetterFromField() {
    setExpectedAnnotations();
    doTest(false);
  }

  public void testNotReplaceAbstractSetterFromField() {
    setExpectedAnnotations();
    doTest(false);
  }

  public void testNotReplaceSetterWithWrongParamFromField() {
    setExpectedAnnotations();
    doTest(false);
  }

  private void setExpectedAnnotations(String... annotationNames) {
    expectedAnnotations = ContainerUtil.set(annotationNames);
  }
}
