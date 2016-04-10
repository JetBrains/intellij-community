package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.ApiVersionAwareLightCodeInsightFixureTestCase;
import de.plushnikov.RequiredApiVersion;
import de.plushnikov.intellij.plugin.provider.LombokAugmentProvider;

/**
 * @author Alexej Kubarev
 */
@RequiredApiVersion("146.1154") // Modifier augmentation has been added in build 146.1154
public class ValModifierTest extends ApiVersionAwareLightCodeInsightFixureTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/augment/modifier";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(PsiAugmentProvider.EP_NAME, new LombokAugmentProvider(), myTestRootDisposable);
    myFixture.addClass("package lombok;\npublic @interface val { }");
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

    PsiType type2 = var.getType();
    assertNotNull(type2);
    assertEquals(PsiType.INT.getCanonicalText(false), type2.getCanonicalText(false));

    assertNotNull(var.getModifierList());
    assertTrue("val should make variable final", var.getModifierList().hasModifierProperty(PsiModifier.FINAL));
  }
}
