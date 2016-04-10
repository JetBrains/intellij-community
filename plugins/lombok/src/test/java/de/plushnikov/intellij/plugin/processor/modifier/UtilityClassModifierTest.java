package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.ApiVersionAwareLightCodeInsightFixureTestCase;
import de.plushnikov.RequiredApiVersion;
import de.plushnikov.intellij.plugin.provider.LombokAugmentProvider;

/**
 * @author Florian Böhm
 */
@RequiredApiVersion("146.1154") // Modifier augmentation has been added in build 146.1154
public class UtilityClassModifierTest extends ApiVersionAwareLightCodeInsightFixureTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/augment/modifier";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(PsiAugmentProvider.EP_NAME, new LombokAugmentProvider(), myTestRootDisposable);
    myFixture.addClass("package lombok.experimental;\npublic @interface UtilityClass { }");
  }

  public void testUtilityClassModifiersField() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiField field = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiField.class);

    assertNotNull(field);
    assertNotNull(field.getModifierList());

    PsiElement parent = field.getParent();

    assertNotNull(parent);
    assertTrue(parent instanceof PsiClass);

    PsiClass parentClass = (PsiClass) parent;

    assertNotNull(parentClass.getModifierList());
    assertTrue("@UtilityClass should make parent class final", parentClass.getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@UtilityClass should make field static", field.getModifierList().hasModifierProperty(PsiModifier.STATIC));
  }

  public void testUtilityClassModifiersInnerClass() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiClass innerClass = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiClass.class);

    assertNotNull(innerClass);
    assertNotNull(innerClass.getModifierList());

    PsiElement parent = innerClass.getParent();

    assertNotNull(parent);
    assertTrue(parent instanceof PsiClass);

    PsiClass parentClass = (PsiClass) parent;

    assertNotNull(parentClass.getModifierList());
    assertTrue("@UtilityClass should make parent class final", ((PsiClass) innerClass.getParent()).getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@UtilityClass should make inner class static", innerClass.getModifierList().hasModifierProperty(PsiModifier.STATIC));
  }

  public void testUtilityClassModifiersMethod() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiMethod method = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiMethod.class);

    assertNotNull(method);
    assertNotNull(method.getModifierList());

    PsiElement parent = method.getParent();

    assertNotNull(parent);
    assertTrue(parent instanceof PsiClass);

    PsiClass parentClass = (PsiClass) parent;

    assertNotNull(parentClass.getModifierList());
    assertTrue("@UtilityClass should make parent class final", ((PsiClass) method.getParent()).getModifierList().hasModifierProperty(PsiModifier.FINAL));
    assertTrue("@UtilityClass should make method static", method.getModifierList().hasModifierProperty(PsiModifier.STATIC));
  }
}
