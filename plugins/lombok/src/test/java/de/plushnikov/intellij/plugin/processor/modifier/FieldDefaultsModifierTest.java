package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexej Kubarev
 */
public class FieldDefaultsModifierTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/augment/modifier";
  }

  //<editor-fold desc="Handling of makeFinal and @NonFinal">

  public void testFieldDefaultsStaticFinal() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(makeFinal = true) should not make static field final", modifierList.hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@FieldDefaults(makeFinal = true) should keep static field package local", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }

  public void testFieldDefaultsFinal() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertTrue("@FieldDefaults(makeFinal = true) should make all @NonFinal fields final", modifierList.hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@FieldDefaults(makeFinal = true) should keep fields package local", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }

  public void testFieldDefaultsFinalFalse() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(makeFinal = false) should make all @NonFinal fields final", modifierList.hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@FieldDefaults(makeFinal = false) should keep fields package local", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }

  public void testFieldDefaultsWithNonFinal() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(makeFinal = true) should not make @NonFinal fields final", modifierList.hasModifierProperty(PsiModifier.FINAL));
  }

  public void testFieldDefaultsWithUtilityClass() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(makeFinal = true) should not make @UtilityClass fields final", modifierList.hasModifierProperty(PsiModifier.FINAL));
  }

  //</editor-fold>

  //<editor-fold desc="Handling of visibility modifiers">

  public void testFieldDefaultsStaticPrivate() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults should not make static field private", modifierList.hasModifierProperty(PsiModifier.PRIVATE));
    assertTrue("@FieldDefaults should keep static field package local", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }


  public void testFieldDefaultsNone() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults should not make fields public", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertFalse("@FieldDefaults should not make fields protected", modifierList.hasModifierProperty(PsiModifier.PROTECTED));
    assertFalse("@FieldDefaults should not make fields private", modifierList.hasModifierProperty(PsiModifier.PRIVATE));
    assertTrue("@FieldDefaults should keep fields as package-private", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }

  public void testFieldDefaultsPublic() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertTrue("@FieldDefaults(level = AccessLevel.PUBLIC) should make non-@PackagePrivate fields public", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertFalse("@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields package-private", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    assertFalse("@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields protected", modifierList.hasModifierProperty(PsiModifier.PROTECTED));
    assertFalse("@FieldDefaults(level = AccessLevel.PUBLIC) should not make fields private", modifierList.hasModifierProperty(PsiModifier.PRIVATE));
  }

  public void testFieldDefaultsProtected() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields public", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertFalse("@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields package-private", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    assertTrue("@FieldDefaults(level = AccessLevel.PROTECTED) should non-@PackagePrivate make fields protected", modifierList.hasModifierProperty(PsiModifier.PROTECTED));
    assertFalse("@FieldDefaults(level = AccessLevel.PROTECTED) should not make fields private", modifierList.hasModifierProperty(PsiModifier.PRIVATE));
  }

  public void testFieldDefaultsPrivate() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields public", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertFalse("@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields protected", modifierList.hasModifierProperty(PsiModifier.PROTECTED));
    assertFalse("@FieldDefaults(level = AccessLevel.PRIVATE) should not make fields package-private", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    assertTrue("@FieldDefaults(level = AccessLevel.PRIVATE) should make non-@PackagePrivate fields private", modifierList.hasModifierProperty(PsiModifier.PRIVATE));
  }

  public void testFieldDefaultsPublicWithPackagePrivate() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults(level = AccessLevel.PUBLIC) should not make @PackagePrivate fields public", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertTrue("@FieldDefaults should keep @PackagePrivate fields package-private", modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
  }

  public void testFieldDefaultsWithExplicitModifier() {

    PsiModifierList modifierList = getFieldModifierListAtCaret();

    assertFalse("@FieldDefaults should not touch fields with explicit modifier", modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertTrue("@FieldDefaults should keep explicit modifier intact", modifierList.hasModifierProperty(PsiModifier.PROTECTED));
  }

  //</editor-fold>

  //<editor-fold desc="Internal support methods">

  @NotNull
  private PsiModifierList getFieldModifierListAtCaret() {
    PsiFile file = loadToPsiFile(getTestName(false) + ".java");
    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

    assertNotNull(field);

    PsiModifierList modifierList = field.getModifierList();
    assertNotNull(modifierList);

    return modifierList;
  }

  //</editor-fold>
}
