package de.plushnikov.intellij.plugin.intention;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;

/**
 * @author Lekanich
 */
@TestApplication
public class ReplaceFromFieldWithLombokAnnotationActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, "/intention/replaceLombok", () -> new ReplaceWithLombokAnnotationAction(),
                           this::wasInvocationSuccessful);

  private Set<String> expectedAnnotations = Collections.emptySet();
  private String expectedAnnotationAccessLevel = PsiModifier.PUBLIC;

  private boolean wasInvocationSuccessful() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
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

  @Test
  public void testReplaceGetterFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceSetterFromField() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplacePrivateAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PRIVATE;
    intention.get().doTest();
  }

  @Test
  public void testReplaceProtectedAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
    intention.get().doTest();
  }

  @Test
  public void testReplacePackageProtectedAccessorsFromField() {
    setExpectedAnnotations(LombokClassNames.GETTER, LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PACKAGE_LOCAL;
    intention.get().doTest();
  }

  @Test
  public void testReplaceGetterFromProtectedMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
    intention.get().doTest();
  }

  @Test
  public void testReplaceSetterFromProtectedMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    expectedAnnotationAccessLevel = PsiModifier.PROTECTED;
    intention.get().doTest();
  }

  @Test
  public void testNotReplaceIncorrectAccessors() {
    setExpectedAnnotations();
    intention.get().doTest(false);
  }

  @Test
  public void testNotReplaceSetterWithAdditionalCode() {
    setExpectedAnnotations();
    intention.get().doTest(false);
  }

  @Test
  public void testReplaceGetterFromMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceSetterFromMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceGetterFromMethod2() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceSetterFromMethod2() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceGetterFromFieldNotCompleteMethod() {
    setExpectedAnnotations(LombokClassNames.GETTER);
    intention.get().doTest();
  }

  @Test
  public void testReplaceSetterFromFieldNotCompleteMethod() {
    setExpectedAnnotations(LombokClassNames.SETTER);
    intention.get().doTest();
  }

  @Test
  public void testNotReplaceAbstractGetterFromField() {
    setExpectedAnnotations();
    intention.get().doTest(false);
  }

  @Test
  public void testNotReplaceAbstractSetterFromField() {
    setExpectedAnnotations();
    intention.get().doTest(false);
  }

  @Test
  public void testNotReplaceSetterWithWrongParamFromField() {
    setExpectedAnnotations();
    intention.get().doTest(false);
  }

  private void setExpectedAnnotations(String... annotationNames) {
    expectedAnnotations = Set.of(annotationNames);
  }
}
