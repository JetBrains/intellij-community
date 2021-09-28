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

  public void testGeneratedCode() {
    PlatformTestUtil.startPerformanceTest("200 unrelated methods", 40000, () -> {
      StringBuilder text = new StringBuilder("import lombok.Getter; @interface Tolerate{} class Foo {");
      for (int i = 0; i < 200; i++) {
        text.append("@Getter int bar").append(i).append(";");
      }

      for (int i = 0; i < 200; i++) {
        text.append("@Tolerate public void m").append(i).append("(){int i = getBar").append(i).append("();}");
      }

      text.append("}");

      myFixture.configureByText("Foo.java", text.toString());
      myFixture.checkHighlighting();
    })
      .assertTiming();
  }
}
