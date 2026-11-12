package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alexej Kubarev
 */
@TestApplication
public class ValueModifierTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/augment/modifier");

  @Test
  public void testValueModifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");

    ReadAction.runBlocking(() -> {
      PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

      assertNotNull(field);
      assertNotNull(field.getModifierList());

      assertTrue(field.getModifierList().hasModifierProperty(PsiModifier.FINAL), "@Value should make variable final");
      assertTrue(field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE), "@Value should make variable private");

      PsiClass clazz = PsiTreeUtil.getParentOfType(field, PsiClass.class);

      assertNotNull(clazz);

      PsiModifierList list = clazz.getModifierList();

      assertNotNull(list);
      assertTrue(list.hasModifierProperty(PsiModifier.FINAL), "@Value should make class final");
      assertFalse(list.hasModifierProperty(PsiModifier.PRIVATE), "@Value should not make class private");
      assertFalse(list.hasModifierProperty(PsiModifier.STATIC), "@Value should not make class static");
    });
  }

  @Test
  public void testValueModifiersStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");

    ReadAction.runBlocking(() -> {
      PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);
      assertNotNull(field);
      assertNotNull(field.getModifierList());

      assertFalse(field.getModifierList().hasModifierProperty(PsiModifier.FINAL), "@Value should not make static variable final");
      assertFalse(field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE), "@Value should not make static variable private");
    });
  }
}
