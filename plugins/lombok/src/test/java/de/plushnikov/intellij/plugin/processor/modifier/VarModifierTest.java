package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Alexej Kubarev
 */
public class VarModifierTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/augment/modifier";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package lombok.experimental;\npublic @interface var { }");
  }

  public void testVarModifiers() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);

    assertNotNull(var);
    assertNotNull(var.getModifierList());
    boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
    assertTrue("var doesn't make variable final", !isFinal);
  }

  public void testVarModifiersEditing() {
    PsiFile file = myFixture.configureByText("a.java", "import lombok.experimental.var;\nclass Foo { {var o = <caret>;} }");
    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertNotNull(var);

    PsiType type1 = var.getType();
    assertNotNull(type1);
    assertEquals("lombok.experimental.var", type1.getCanonicalText(false));

    myFixture.type('1');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertTrue(var.isValid());

    assertNotNull(var.getModifierList());
    boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
    assertTrue("var doesn't make variable final", !isFinal);
  }
}
