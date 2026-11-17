package com.intellij.lang.properties;

import com.intellij.lang.properties.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class DuplicatePropertyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, PluginPathManager.getPluginHomePath("properties") + "/tests/testData");

  //ProblemDescriptor.getLineNumber()==1 for this inspection (there is no RefPropertyElement thus PsiElement -> PsiFile)
  private DuplicatePropertyInspection myTool;

  @BeforeEach
  public void setUp() {
    myTool = new DuplicatePropertyInspection();
  }

  private void doTest() {
    javaInspection.get().doTest("duplicateProperty/" + javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testDuplicateValues() {
    doTest();
  }

  @Test
  public void testDuplicateValuesCurrentFileAnalysis() {
    doTest();
  }

  @Test
  public void testDuplicateValuesInDifferentFiles() {
    myTool.CURRENT_FILE = false;
    myTool.MODULE_WITH_DEPENDENCIES = true;
    doTest();
  }

  @Test
  public void testDuplicateKeysWithDifferentValues() {
    myTool.CURRENT_FILE = false;
    myTool.MODULE_WITH_DEPENDENCIES = true;
    myTool.CHECK_DUPLICATE_KEYS = false;
    myTool.CHECK_DUPLICATE_VALUES = false;
    myTool.CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = true;
    doTest();
  }

  @Test
  public void testDuplicateKeys() {
    myTool.CURRENT_FILE = false;
    myTool.MODULE_WITH_DEPENDENCIES = true;
    myTool.CHECK_DUPLICATE_KEYS = true;
    myTool.CHECK_DUPLICATE_VALUES = false;
    myTool.CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = false;
    doTest();
  }

  @Test
  public void testDuplicateKeysWithAndWithoutDifferent() {
    myTool.CURRENT_FILE = false;
    myTool.MODULE_WITH_DEPENDENCIES = true;
    myTool.CHECK_DUPLICATE_KEYS = true;
    myTool.CHECK_DUPLICATE_VALUES = false;
    myTool.CHECK_DUPLICATE_KEYS_WITH_DIFFERENT_VALUES = true;
    doTest();
  }
}
