package com.intellij.codeInspection;

import com.intellij.codeInspection.visibility.VisibilityInspection;

public class VisibilityInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("visibility/" + getTestName(false), getTool());
  }

  private VisibilityInspection getTool() {
    return (VisibilityInspection)getManager().getCurrentProfile().getInspectionTool(VisibilityInspection.SHORT_NAME);
  }

  public void testinnerConstructor() throws Exception {
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    getTool().SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testpackageLevelTops() throws Exception {
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    getTool().SUGGEST_PRIVATE_FOR_INNERS = false;

    doTest();
  }

  public void testSCR5008() throws Exception {
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    getTool().SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR6856() throws Exception {
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    getTool().SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR11792() throws Exception {
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    getTool().SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    getTool().SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }
}
