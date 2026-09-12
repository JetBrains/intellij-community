package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class UnusedLoggerInspectionTest extends LombokInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/unusedLogger";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnusedLoggerInspection();
  }

  public void testUnusedLogger() {
    doTest();
    checkQuickFixAll();
  }

  public void testRemoveUnusedLoggerImport() {
    doTest();
    checkQuickFixAll();
  }

  public void testInvalidAnnotationTargetsWithoutClassLogger() {
    checkInvalidAnnotationTargets("", "");
  }

  public void testInvalidAnnotationTargetsWithClassLogger() {
    checkInvalidAnnotationTargets("@Log", "Object logger = log;");
  }

  private void checkInvalidAnnotationTargets(String classAnnotation, String loggerUsage) {
    myFixture.configureByText("InvalidTargets.java", """
      import lombok.extern.java.Log;

      %s
      class InvalidTargets {
        <error>@Log</error>
        private String field;

        <error>@Log</error>
        InvalidTargets() {}

        <error>@Log</error>
        void method(<error>@Log</error> String parameter) {
          <error>@Log</error> String local = parameter;
          %s
        }

        Object value = new <error>@Log</error> Object();
      }
      """.formatted(classAnnotation, loggerUsage));
    myFixture.checkHighlighting();
  }
}
