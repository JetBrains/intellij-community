package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Florian Böhm
 */
@TestApplication
public class UtilityClassModifierTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/augment/modifier");

  @Test
  public void testUtilityClassModifiersField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

      assertNotNull(field);
      assertNotNull(field.getModifierList());

      PsiElement parent = field.getParent();

      assertNotNull(parent);
      assertTrue(parent instanceof PsiClass);

      PsiClass parentClass = (PsiClass) parent;

      assertNotNull(parentClass.getModifierList());
      assertTrue(parentClass.getModifierList().hasModifierProperty(PsiModifier.FINAL), "@UtilityClass should make parent class final");
      assertTrue(field.getModifierList().hasModifierProperty(PsiModifier.STATIC), "@UtilityClass should make field static");
    });
  }

  @Test
  public void testUtilityClassModifiersInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiClass innerClass = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiClass.class);

      assertNotNull(innerClass);
      assertNotNull(innerClass.getModifierList());

      PsiElement parent = innerClass.getParent();

      assertNotNull(parent);
      assertTrue(parent instanceof PsiClass);

      PsiClass parentClass = (PsiClass) parent;

      assertNotNull(parentClass.getModifierList());
      assertTrue(((PsiClass) innerClass.getParent()).getModifierList().hasModifierProperty(PsiModifier.FINAL), "@UtilityClass should make parent class final");
      assertTrue(innerClass.getModifierList().hasModifierProperty(PsiModifier.STATIC), "@UtilityClass should make inner class static");
    });
  }

  @Test
  public void testUtilityClassModifiersMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiMethod method = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiMethod.class);

      assertNotNull(method);
      assertNotNull(method.getModifierList());

      PsiElement parent = method.getParent();

      assertNotNull(parent);
      assertTrue(parent instanceof PsiClass);

      PsiClass parentClass = (PsiClass) parent;

      assertNotNull(parentClass.getModifierList());
      assertTrue(((PsiClass) method.getParent()).getModifierList().hasModifierProperty(PsiModifier.FINAL), "@UtilityClass should make parent class final");
      assertTrue(method.getModifierList().hasModifierProperty(PsiModifier.STATIC), "@UtilityClass should make method static");
    });
  }
}
