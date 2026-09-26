package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.controlflow.PointlessBooleanExpressionInspection;


/**
 * @author Lekanich
 */
public class PointlessBooleanExpressionInspectionTest extends LombokInspectionTest {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/pointlessBooleanExpression";
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new PointlessBooleanExpressionInspection();
  }

  public void testPointlessBooleanExpressionBuilderDefault() {
    doTest();
  }
}
