package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * Test actions that various inspections add.
 * User: dcheryasov
 * Date: Nov 29, 2008 12:47:08 AM
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyLightFixtureTestCase {

  public void testAddImport() throws Exception {
    doInspectionTest("AddImport.py", PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDoc() throws Exception {
    doInspectionTest("AddImportDoc.py", PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testQualifyByImport() throws Exception {
    doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.qualify.with.module"), true, true);
  }

  public void testAddToImportFromList() throws Exception {
    doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.use.import"), true, true);
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() throws Exception {
    doInspectionTest("AddSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self"), true, true);
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

  // we don't really need quickfixes for convering Py3 syntax into Py2
  public void _testReplaceExceptPartTo2() throws Exception {
    doInspectionTest("ReplaceExceptPartTo2.py", PyUnsupportedFeaturesInspection.class, PyBundle.message("QFIX.replace.except.part"), true,
                     true);
  }

  public void testReplaceNotEqOperator() throws Exception {
    doInspectionTestWithPy3k("ReplaceNotEqOperator.py", PyUnsupportedFeaturesInspection.class,
                             PyBundle.message("QFIX.replace.noteq.operator"), true, true);
  }

  public void testReplaceBackquoteExpression() throws Exception {
    doInspectionTestWithPy3k("ReplaceBackQuoteExpression.py", PyUnsupportedFeaturesInspection.class,
                             PyBundle.message("QFIX.replace.backquote.expression"), true, true);
  }

  public void testReplaceMethod() throws Exception {
    doInspectionTestWithPy3k("ReplaceMethod.py", PyUnsupportedFeaturesInspection.class, PyBundle.message("QFIX.replace.method"),
                             true, true);
  }

  public void testRemoveLeadingU() throws Exception {
    doInspectionTestWithPy3k("RemoveLeadingU.py", PyUnsupportedFeaturesInspection.class, PyBundle.message("QFIX.remove.leading.u"), true, true);
  }

  public void testTrailingL() throws Exception {
    doInspectionTestWithPy3k("RemoveTrailingL.py", PyUnsupportedFeaturesInspection.class, PyBundle.message("QFIX.remove.trailing.l"), true, true);
  }

  public void testReplaceOctalNumericLiteral() throws Exception {
    doInspectionTestWithPy3k("ReplaceOctalNumericLiteral.py", PyUnsupportedFeaturesInspection.class,
                             PyBundle.message("QFIX.replace.octal.numeric.literal"), true, true);
  }

  public void testReplaceRaiseStatement() throws Exception {
    doInspectionTestWithPy3k("ReplaceRaiseStatement.py", PyUnsupportedFeaturesInspection.class,
                             PyBundle.message("QFIX.replace.raise.statement"), true, true);
  }

  public void testReplaceExceptPartTo3() throws Exception {
    doInspectionTestWithPy3k("ReplaceExceptPartTo3.py", PyUnsupportedFeaturesInspection.class, PyBundle.message("QFIX.replace.except.part"),
                             true, true);
  }

  public void testReplaceListComprehensions() throws Exception {
    doInspectionTestWithPy3k("ReplaceListComprehensions.py", PyUnsupportedFeaturesInspection.class,
                             PyBundle.message("QFIX.replace.list.comprehensions"), true, true);
  }

  public void testDictCreation() throws Exception {
    doInspectionTest("DictCreation.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testTransformClassicClass() throws Exception {
    doInspectionTest("TransformClassicClass.py", PyClassicStyleClassInspection.class,
                     PyBundle.message("QFIX.classic.class.transform"), true, true);
  }

  protected void doInspectionTestWithPy3k(@NonNls String testFileName,
                                        final Class inspectionClass,
                                        @NonNls String quickFixName,
                                        boolean applyFix,
                                        boolean available) throws Exception {
    PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL = LanguageLevel.PYTHON30;
    PythonLanguageLevelPusher.pushLanguageLevel(myFixture.getProject());
    try {
      doInspectionTest(testFileName, inspectionClass, quickFixName, applyFix, available);
    }
    finally {
      PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL = null;
    }
  }

  protected
  @NonNls
  String getTestDataPath() {
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

  private static Sdk createMockJdk(String jdkHome) {
    File jdkHomeFile = new File(jdkHome);
    if (!jdkHomeFile.exists()) return null;

    final ProjectJdkImpl jdk = new ProjectJdkImpl("2.5", PythonSdkType.getInstance());
    final SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = jdkHome.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.commitChanges();

    PythonSdkType.getInstance().setupSdkPaths(jdk);

    jdk.setVersionString("2.5");
    return jdk;
  }

  protected static class PyWithSdkProjectDescriptor extends PyLightProjectDescriptor {
    @Override
    public Sdk getSdk() {
      return createMockJdk(PathManager.getHomePath() + "/plugins/python/testData/mockPythonJDK");
    }
  }

  private static final LightProjectDescriptor ourProjectDescriptor = new PyWithSdkProjectDescriptor();

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourProjectDescriptor;
  }
}
