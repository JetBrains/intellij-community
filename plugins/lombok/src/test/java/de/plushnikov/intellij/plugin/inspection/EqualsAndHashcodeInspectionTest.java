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
    configureAndTest("""
      <warning descr="The field 'a' does not exist">@lombok.EqualsAndHashCode(of={"a"})</warning>
      class Main {
        int i;
        String s;
        Float f;
      }
      """);
  }
}

