package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

/**
 * @author Alexej Kubarev
 */
public class ValueModifierTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/augment/modifier";
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

    PsiModifierList list = clazz.getModifierList();

    assertNotNull(list);
    assertTrue("@Value should make class final", list.hasModifierProperty(PsiModifier.FINAL));
    assertFalse("@Value should not make class private", list.hasModifierProperty(PsiModifier.PRIVATE));
    assertFalse("@Value should not make class static", list.hasModifierProperty(PsiModifier.STATIC));
  }

  public void testValueModifiersStatic() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");

    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);
    assertNotNull(field);
    assertNotNull(field.getModifierList());

    assertFalse("@Value should not make static variable final", field.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertFalse("@Value should not make static variable private", field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE));
  }
}
