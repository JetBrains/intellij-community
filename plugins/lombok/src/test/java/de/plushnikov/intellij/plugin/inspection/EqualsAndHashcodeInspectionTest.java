package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;

public class EqualsAndHashcodeInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/"  + TEST_DATA_INSPECTION_DIRECTORY + "/equalsandhashcode";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokInspection();
  }

  public void testOfWithUnknownFields() {
    myFixture.configureByText("Main.java", "<warning descr=\"The field 'a' does not exist\">@lombok.EqualsAndHashCode(of={\"a\"})</warning>\n" +
                                           "class Main {\n" +
                                           "  int i;\n" +
                                           "  String s;\n" +
                                           "  Float f;\n" +
                                           "}\n");
    myFixture.checkHighlighting();
  }
}

