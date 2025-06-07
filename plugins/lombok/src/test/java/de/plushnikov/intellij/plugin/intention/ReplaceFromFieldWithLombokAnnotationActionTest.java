package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * @author Lekanich
 */
public class ReplaceFromFieldWithLombokAnnotationActionTest extends LombokIntentionActionTest {
  private Set<String> expectedAnnotations = Collections.emptySet();
  private String expectedAnnotationAccessLevel = PsiModifier.PUBLIC;

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/replaceLombok";
  }

  @Override
  public ModCommandAction getAction() {
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

    return ContainerUtil.and(expectedAnnotations, field::hasAnnotation) &&
           field.getAnnotations().length == expectedAnnotations.size() &&
           Arrays.stream(field.getAnnotations()).map(LombokProcessorUtil::getMethodModifier).allMatch(expectedAnnotationAccessLevel::equals);
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

  public void testReplacePrivateAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PRIVATE;
    doTest();
  }

  public void testReplaceProtectedAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
    doTest();
  }

  public void testReplacePackageProtectedAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PACKAGE_LOCAL;
    doTest();
  }

  public void testReplaceGetterFromProtectedMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
    doTest();
  }

  public void testReplaceSetterFromProtectedMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
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
    expectedAnnotations = Set.of(annotationNames);
  }
}
