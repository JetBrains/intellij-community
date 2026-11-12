package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class RenameFieldActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testFieldRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", """
      @lombok.Data
      public class Main {
          private String someString<caret>;

          public static void main(String[] args) {
              final Main main = new Main();
              main.setSomeString("Hello World");
              System.out.println(main.getSomeString());
          }
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.renameElementAtCaretUsingHandler("someString2");

      myFixture.checkHighlighting();
    });
  }
}
