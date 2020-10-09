package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
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

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public void testPointlessBooleanExpressionBuilderDefault() {
    doTest();
  }
}
