package de.plushnikov.intellij.plugin.performance;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class PerformanceTest extends AbstractLombokLightCodeInsightTestCase {

  public void testFieldDefaults() {
    final String testName = getTestName(true);
    loadToPsiFile("/performance/" + testName + "/lombok.config");
    final PsiFile psiFile = loadToPsiFile("/performance/" + testName + "/HugeClass.java");
    PlatformTestUtil.startPerformanceTest(getTestName(false), 500, () -> {
      type(' ');
      PsiDocumentManager.getInstance(getProject()).commitDocument(getEditor().getDocument());
      ((PsiJavaFileImpl) psiFile).getClasses()[0].getFields()[0].hasModifierProperty(PsiModifier.FINAL);

      backspace();
      PsiDocumentManager.getInstance(getProject()).commitDocument(getEditor().getDocument());
      ((PsiJavaFileImpl) psiFile).getClasses()[0].getFields()[0].hasModifierProperty(PsiModifier.FINAL);
    }).assertTiming();
  }

  private void backspace() {
    LightPlatformCodeInsightTestCase.backspace(getEditor(), getProject());
  }

  private void type(char c) {
    LightPlatformCodeInsightTestCase.type(c, getEditor(), getProject());
  }
}
