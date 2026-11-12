package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alexej Kubarev
 */
@TestApplication
public class VarModifierTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/augment/modifier");

  @Test
  public void testVarModifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);

      assertNotNull(var);
      assertNotNull(var.getModifierList());
      boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
      assertFalse(isFinal, "var doesn't make variable final");
    });
  }

  @Test
  public void testVarModifiersEditing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
      assertNotNull(var);
      assertNotNull(var.getType());

      myFixture.type('1');
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assertTrue(var.isValid());
      assertEquals(PsiTypes.intType(), var.getType());

      assertNotNull(var.getModifierList());
      boolean isFinal = var.getModifierList().hasModifierProperty(PsiModifier.FINAL);
      assertFalse(isFinal, "var doesn't make variable final");
    });
  }
}
