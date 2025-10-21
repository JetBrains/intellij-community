package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class Lombok18FlagUsageInspectionTest extends LombokInspectionTest {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokFlagUsageInspection();
  }

  /**
   * Test for var on local variables with flag usage set to WARNING
   */
  public void testVarLocalVariableFlagUsageWarning() {
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
  public void testVarParameterFlagUsageWarning() {
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
