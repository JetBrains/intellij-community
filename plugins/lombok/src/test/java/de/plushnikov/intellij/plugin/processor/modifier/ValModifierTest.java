package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

/**
 * @author Alexej Kubarev
 */
public class ValModifierTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/augment/modifier";
  }

  public void testValModifiers() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);

    assertNotNull(var);
    assertNotNull(var.getModifierList());
    assertTrue("val should make variable final", var.getModifierList().hasModifierProperty(PsiModifier.FINAL));
  }

  public void testValModifiersEditing() {
    PsiFile file = myFixture.configureByText("a.java", "import lombok.val;\nclass Foo { {val o = <caret>;} }");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertNotNull(var);

    PsiType type1 = var.getType();
    assertNotNull(type1);
    assertEquals("lombok.val", type1.getCanonicalText(false));

    myFixture.type('1');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(var.isValid());

    assertNotNull(var.getModifierList());
    assertTrue("val should make variable final", var.getModifierList().hasModifierProperty(PsiModifier.FINAL));
  }
}
