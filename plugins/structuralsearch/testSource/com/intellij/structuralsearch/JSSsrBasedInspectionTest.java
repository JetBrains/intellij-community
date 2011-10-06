package com.intellij.structuralsearch;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.javascript.JavaScriptFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.InspectionTestCase;

import java.util.Collections;

/**
 * @author Eugene.Kudelevsky
 */
public class JSSsrBasedInspectionTest extends InspectionTestCase {
  private LocalInspectionToolWrapper myWrapper;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspection = new SSBasedInspection();
    myWrapper = new LocalInspectionToolWrapper(myInspection);
  }

  public void testExpressionStatement() throws Exception {
    doTest("console.log", "console.log");
  }

  public void testExpressionStatement1() throws Exception {
    doTest("console.log()", "console.log");
  }

  public void testExpressionStatement2() throws Exception {
    doTest("console.log();", "console.log");
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/structuralsearch/testData";
  }

  private void doTest(String pattern, String patternName) throws Exception {
    final SearchConfiguration configuration = new SearchConfiguration();
    configuration.setName(patternName);
    final MatchOptions options = new MatchOptions();
    options.setSearchPattern(pattern);
    options.setFileType(JavaScriptFileType.INSTANCE);
    configuration.setMatchOptions(options);

    myInspection.setConfigurations(Collections.singletonList((Configuration)configuration),
                                   myProject);
    myInspection.projectOpened(getProject());
    doTest("jsInspection/" + getTestName(true), myWrapper);
  }
}

