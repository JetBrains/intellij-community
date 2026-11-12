package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.application.ReadAction;
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
import org.jetbrains.annotations.NotNull;
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
public class FieldDefaultsModifierTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/augment/modifier");



  //<editor-fold desc="Handling of makeFinal and @NonFinal">

  @Test
  public void testFieldDefaultsStaticFinal() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.FINAL), "@FieldDefaults(makeFinal = true) should not make static field final");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(makeFinal = true) should keep static field package local");
    });
  }

  @Test
  public void testFieldDefaultsFinal() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertTrue(modifierList.hasModifierProperty(PsiModifier.FINAL), "@FieldDefaults(makeFinal = true) should make all @NonFinal fields final");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(makeFinal = true) should keep fields package local");
    });
  }

  @Test
  public void testFieldDefaultsFinalFalse() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.FINAL), "@FieldDefaults(makeFinal = false) should make all @NonFinal fields final");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(makeFinal = false) should keep fields package local");
    });
  }

  @Test
  public void testFieldDefaultsWithNonFinal() {
    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.FINAL), "@FieldDefaults(makeFinal = true) should not make @NonFinal fields final");
    });
  }

  @Test
  public void testFieldDefaultsWithUtilityClass() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.FINAL), "@FieldDefaults(makeFinal = true) should not make @UtilityClass fields final");
    });
  }

  //</editor-fold>

  //<editor-fold desc="Handling of visibility modifiers">

  @Test
  public void testFieldDefaultsStaticPrivate() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PRIVATE), "@FieldDefaults should not make static field private");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults should keep static field package local");
    });
  }

  @Test
  public void testFieldDefaultsNone() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults should not make fields public");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PROTECTED), "@FieldDefaults should not make fields protected");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PRIVATE), "@FieldDefaults should not make fields private");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults should keep fields as package-private");
    });
  }

  @Test
  public void testFieldDefaultsPublic() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults(level = AccessLevel.PUBLIC) should make non-@PackagePrivate fields public");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields package-private");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PROTECTED), "@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields protected");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PRIVATE), "@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields private");
    });
  }

  @Test
  public void testFieldDefaultsProtected() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields public");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields package-private");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PROTECTED), "@FieldDefaults(level = AccessLevel.PROTECTED) should non-@PackagePrivate make fields protected");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PRIVATE), "@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields private");
    });
  }

  @Test
  public void testFieldDefaultsPrivate() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields public");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PROTECTED), "@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields protected");
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields package-private");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PRIVATE), "@FieldDefaults(level = AccessLevel.PRIVATE) should make non-@PackagePrivate fields private");
    });
  }

  @Test
  public void testFieldDefaultsPublicWithPackagePrivate() {
    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults(level = AccessLevel.PUBLIC) should not make @PackagePrivate fields public");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL), "@FieldDefaults should keep @PackagePrivate fields package-private");
    });
  }

  @Test
  public void testFieldDefaultsWithExplicitModifier() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    ReadAction.runBlocking(() -> {
      assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC), "@FieldDefaults should not touch fields with explicit modifier");
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PROTECTED), "@FieldDefaults should keep explicit modifier intact");
    });
  }

  //</editor-fold>

  //<editor-fold desc="Internal support methods">

  @NotNull
  private PsiModifierList getFieldModifierListAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = LombokTestUtil.loadToPsiFile(myFixture, myFixture.getTestName(false) + ".java");
    return ReadAction.computeBlocking(() -> {
      PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

      assertNotNull(field);

      PsiModifierList modifierList = field.getModifierList();
      assertNotNull(modifierList);

      return modifierList;
    });
  }

  //</editor-fold>
}
