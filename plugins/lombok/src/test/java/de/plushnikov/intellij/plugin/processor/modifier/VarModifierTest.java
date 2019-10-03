package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Alexej Kubarev
 */
public class VarModifierTest extends LightJavaCodeInsightFixtureTestCase {

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
    assertEquals(PsiPrimitiveType.INT, var.getType());

    assertNotNull(var.getModifierList());
    boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
    assertFalse("var doesn't make variable final", isFinal);
  }
}
