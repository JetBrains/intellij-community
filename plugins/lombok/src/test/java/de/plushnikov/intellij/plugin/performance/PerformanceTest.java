package de.plushnikov.intellij.plugin.performance;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorOpenTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@PerformanceUnitTest
@StressTestApplication
public class PerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testFieldDefaults(TestInfo testInfo) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(true);
    LombokTestUtil.loadToPsiFile(myFixture, "/performance/" + testName + "/lombok.config");
    final PsiFile psiFile = LombokTestUtil.loadToPsiFile(myFixture, "/performance/" + testName + "/HugeClass.java");
    String launchName = myFixture.getTestName(false);
    EdtTestUtil.runInEdtAndWait(() -> Benchmark.newBenchmark(launchName, () -> {
      type(' ');
      PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(myFixture.getEditor().getDocument());
      ((PsiJavaFileImpl)psiFile).getClasses()[0].getFields()[0].hasModifierProperty(PsiModifier.FINAL);

      backspace();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(myFixture.getEditor().getDocument());
      ((PsiJavaFileImpl)psiFile).getClasses()[0].getFields()[0].hasModifierProperty(PsiModifier.FINAL);
    }).start(testInfo.getTestMethod().orElseThrow(), launchName));
  }

  private void backspace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Editor editor = myFixture.getEditor();
    CommandProcessor.getInstance().executeCommand(
      myFixture.getProject(), () -> EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE, true), "", null, editor.getDocument());
    EditorOpenTestUtil.awaitPendingNavigationAndEditorOpenIfEnabled(myFixture.getProject());
  }

  private void type(char c) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TypedAction.getInstance().actionPerformed(myFixture.getEditor(), c, DataManager.getInstance().getDataContext());
  }

  @Test
  public void testGeneratedCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Benchmark.newBenchmark("300 unrelated methods", () -> {
        StringBuilder text = new StringBuilder("import lombok.Getter; import lombok.Setter; @interface Tolerate{} class Foo {");
        for (int i = 0; i < 200; i++) {
          text.append("@Getter @Setter int bar").append(i).append(";");
        }

        for (int i = 0; i < 200; i++) {
          text.append("@Tolerate public void m").append(i).append("(){int i = getBar").append(i).append("();}");
        }

        for (int i = 0; i < 200; i++) {
          text.append("@Tolerate public void s").append(i).append("(){setBar").append(i).append("(0);}");
        }

        text.append("}");

        myFixture.configureByText("Foo.java", text.toString());
        myFixture.checkHighlighting();
      })
      .start();
  }

  @Test
  public void testGeneratedCodeThroughStubs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Benchmark.newBenchmark("200 unrelated methods", () -> {
        StringBuilder text = new StringBuilder("import lombok.Getter; import lombok.Setter; class Foo {\n");
        for (int i = 0; i < 200; i++) {
          text.append("@Getter @Setter int bar").append(i).append(";\n");
        }
        text.append("}");
        myFixture.configureByText("Foo.java", text.toString());

        StringBuilder barText = new StringBuilder("class Bar {void m(Foo foo){\n");
        for (int i = 0; i < 200; i++) {
          barText.append("foo.setBar").append(i).append("(0);\n");
        }
        barText.append("}\n}");
        myFixture.configureByText("Bar.java", barText.toString());

        myFixture.checkHighlighting();
      })
      .start();
  }

  @Test
  public void testDataPerformance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    StringBuilder classText = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      classText.append("""
                             @lombok.Data
                             @lombok.EqualsAndHashCode
                             @lombok.ToString
                             """);
      classText.append("class SomeClass").append(i).append("{\n");
      for (int j = 0; j < 30; j++) {
        classText.append("String bar").append(j).append(";\n");
      }
      if (i > 0) {
        for (int j = 0; j < 30; j++) {
          classText.append("""
                                 String getFoo%1$d_%2$d(SomeClass%1$d bar) {
                                   return bar.getBar%2$d() + bar.toString() + bar.hashCode();
                                 }
                                 """.formatted(i - 1, j));
        }
      }
      classText.append("}");
    }

    Benchmark.newBenchmark("@Data/@EqualsAndHashCode/@ToString performance", () -> {
        myFixture.configureByText("Bar.java", classText.toString());
        myFixture.checkHighlighting();
      })
      .start();
  }
}
