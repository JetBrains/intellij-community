package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public abstract class LombokInspectionTest extends LightJavaInspectionTestCase {
  static final String TEST_DATA_INSPECTION_DIRECTORY = "testData/inspection";

  @Override
  protected String getBasePath() {
    return "/plugins/lombok/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }

  protected void configureAndTest(String text) {
    String className = getTestName(false);
    String fileName = className + ".java";

    myFixture.configureByText(fileName, text.formatted(className));
    myFixture.checkHighlighting();
  }
}
