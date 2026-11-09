package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokTestUtil.lombokInspectionFixture;

@TestApplication
public class Lombok18FlagUsageInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lombokInspectionFixture(projectFixture, () -> new LombokFlagUsageInspection(), "");

  /**
   * Test for var on local variables with flag usage set to WARNING
   */
  @Test
  public void testVarLocalVariableFlagUsageWarning() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String testClassText = """
      import lombok.var;

      public class VarLocalVariableFlagUsageWarningTest {
        public void method() {
          <warning descr="Use of @lombok.var is flagged according to Lombok configuration.">var</warning> x = "test";
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.var.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("VarLocalVariableFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for var on parameters with flag usage set to WARNING
   */
  @Test
  public void testVarParameterFlagUsageWarning() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final @Language("JAVA") String testClassText = """
      import lombok.var;
      import java.util.Arrays;
      import java.util.List;

      public class VarParameterFlagUsageWarningTest {
        public void method() {
          List<String> items = Arrays.asList("a", "b", "c");
          for (<warning descr="Use of @lombok.var is flagged according to Lombok configuration.">var</warning> item : items) {
            System.out.println(item);
          }
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.var.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("VarParameterFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }
}
