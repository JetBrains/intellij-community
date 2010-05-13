package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.*;
import org.jetbrains.annotations.NonNls;

/**
 * Test actions that various inspections add.
 * User: dcheryasov
 * Date: Nov 29, 2008 12:47:08 AM
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyLightFixtureTestCase {

  public void testAddImport() throws Exception {
    doInspectionTest(new String[] { "AddImport.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDoc() throws Exception {
    doInspectionTest(new String[] { "AddImportDoc.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDocComment() throws Exception {  // PY-728
    doInspectionTest(new String[] { "AddImportDocComment.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testQualifyByImport() throws Exception {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldPreferFrom = settings.PREFER_FROM_IMPORT;
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.PREFER_FROM_IMPORT = false;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.qualify.with.module"), true, true);
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldPreferFrom;
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }

  public void testAddToImportFromList() throws Exception {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.use.import"), true, true);
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() throws Exception {
    doInspectionTest("AddSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "self"), true, true);
  }

  public void testAddCls() throws Exception {
    doInspectionTest("AddCls.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "cls"), true, true);
  }

  public void testRenameToSelf() throws Exception {
    doInspectionTest("RenameToSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.rename.parameter.to.$0", "self"), true,
                     true);
  }

  public void testAddFieldFromMethod() throws Exception {
    doInspectionTest("AddFieldFromMethod.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddFieldFromInstance() throws Exception {
    doInspectionTest("AddFieldFromInstance.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddFieldAddConstructor() throws Exception {
    doInspectionTest("AddFieldAddConstructor.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"),
                     true, true);
  }

  public void testAddFieldNewConstructor() throws Exception {
    doInspectionTest("AddFieldNewConstructor.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"),
                     true, true);
  }

  public void testAddMethodFromInstance() throws Exception {
    doInspectionTest("AddMethodFromInstance.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddMethodFromMethod() throws Exception {
    doInspectionTest("AddMethodFromMethod.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testRemoveTrailingSemicolon() throws Exception {
    doInspectionTest("RemoveTrailingSemicolon.py", PyTrailingSemicolonInspection.class, PyBundle.message("QFIX.remove.trailing.semicolon"),
                     true, true);
  }

  public void testDictCreation() throws Exception {
    doInspectionTest("DictCreation.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testTransformClassicClass() throws Exception {
    doInspectionTest("TransformClassicClass.py", PyClassicStyleClassInspection.class,
                     PyBundle.message("QFIX.classic.class.transform"), true, true);
  }

  public void testAddGlobalQuickFix() throws Exception {
    doInspectionTest("AddGlobalStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testAddGlobalExistingQuickFix() throws Exception {
    doInspectionTest("AddGlobalExistingStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testSimplifyBooleanCheckQuickFix() throws Exception {
    doInspectionTest("SimplifyBooleanCheck.py", PySimplifyBooleanCheckInspection.class,
                     PyBundle.message("QFIX.simplify"), true, true);
  }

  public void testFromFutureImportQuickFix() throws Exception {
    doInspectionTest("MoveFromFutureImport.py", PyFromFutureImportInspection.class,
                     PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testComparisonWithNoneQuickFix() throws Exception {
    doInspectionTest("ComparisonWithNone.py", PyComparisonWithNoneInspection.class,
                     PyBundle.message("QFIX.replace.equality"), true, true);
  }

  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  protected void doInspectionTest(@TestDataFile @NonNls String testFileName,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available) throws Exception {
    doInspectionTest(new String[]{testFileName}, inspectionClass, quickFixName, applyFix, available);
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   *
   * @param testFiles       names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName    how the resulting fix should be named (the human-readable name users see)
   * @param applyFix        true if the fix needs to be applied
   * @param available       true if the fix should be available, false if it should be explicitly not available.
   * @throws Exception
   */
  protected void doInspectionTest(@NonNls String[] testFiles,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available) throws Exception {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(quickFixName);
    if (available) {
      assertNotNull(intentionAction);
      if (applyFix) {
        myFixture.launchAction(intentionAction);

        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertNull(intentionAction);
    }
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos, name.length());
  }
}
