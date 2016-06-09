package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import de.plushnikov.intellij.plugin.provider.LombokAugmentProvider;

/**
 * @author Alexej Kubarev
 */
public class ValueModifierTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/augment/modifier";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(PsiAugmentProvider.EP_NAME, new LombokAugmentProvider(), myTestRootDisposable);
    myFixture.addClass("package lombok;\npublic @interface Value { }");
  }

  public void testValueModifiers() {

    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");

    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

    assertNotNull(field);
    assertNotNull(field.getModifierList());

    assertTrue("@Value should make variable final", field.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@Value should make variable private", field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE));

    PsiClass clazz = PsiTreeUtil.getParentOfType(field, PsiClass.class);

    assertNotNull(clazz);
    assertNotNull(clazz.getModifierList());
    assertTrue("@Value should make class final", clazz.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertFalse("@Value should not make class private", clazz.getModifierList().hasModifierProperty(PsiModifier.PRIVATE));
    assertFalse("@Value should not make class static", clazz.getModifierList().hasModifierProperty(PsiModifier.STATIC));
  }
}
