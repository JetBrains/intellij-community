package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

/**
 * @author Alexej Kubarev
 */
public class VarModifierTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/augment/modifier";
  }

  public void testVarModifiers() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);

    assertNotNull(var);
    assertNotNull(var.getModifierList());
    boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
    assertFalse("var doesn't make variable final", isFinal);
  }

  public void testVarModifiersEditing() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertNotNull(var);
    assertNotNull(var.getType());

    myFixture.type('1');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(var.isValid());
    assertEquals(PsiType.INT, var.getType());

    assertNotNull(var.getModifierList());
    boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
    assertFalse("var doesn't make variable final", isFinal);
  }
}
