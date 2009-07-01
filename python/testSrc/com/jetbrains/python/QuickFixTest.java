package com.jetbrains.python;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.inspections.PyMethodParametersInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Test actions that various inspections add.
 * User: dcheryasov
 * Date: Nov 29, 2008 12:47:08 AM
 */
public class QuickFixTest extends DaemonAnalyzerTestCase {

  public void testAddImport() throws Exception {
    doInspectionTest("AddImport.py", PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDoc() throws Exception {
    doInspectionTest("AddImportDoc.py", PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testQualifyByImport() throws Exception {
    doInspectionTest(
      new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"},
      PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.use.import"), true, true
    );
  }

  public void testAddToImportFromList() throws Exception {
    doInspectionTest(
      new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"},
      PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.use.import"), true, true
    );
  }
  // TODO: add a test for multiple variants of above

 // TODO: add tests for stub indexes-based autoimport of unimported somehow.   

  public void testAddSelf() throws Exception {
    doInspectionTest("AddSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self"), true, true);
  }

  public void testRenameToSelf() throws Exception {
    doInspectionTest("RenameToSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.rename.parameter.to.self"), true, true);
  }

  public void testAddFieldFromMethod() throws Exception {
    doInspectionTest(
      "AddFieldFromMethod.py",
      PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"), 
      true, true
    );
  }

  public void testAddFieldFromInstance() throws Exception {
    doInspectionTest(
      "AddFieldFromInstance.py",
      PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"),
      true, true
    );
  }

  public void testAddFieldAddConstructor() throws Exception {
    doInspectionTest(
      "AddFieldAddConstructor.py",
      PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"),
      true, true
    );
  }

  public void testAddMethodFromInstance() throws Exception {
    doInspectionTest(
      "AddMethodFromInstance.py",
      PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
      true, true
    );
  }

  public void testAddMethodFromMethod() throws Exception {
    doInspectionTest(
      "AddMethodFromMethod.py",
      PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
      true, true
    );
  }

  protected VirtualFile[] loadFiles(String[] names) {
    VirtualFile[] ret = new VirtualFile[names.length];
    String prefix = getTestDataPath();
    for (int i=0; i < names.length; i += 1) {
      ret[i] = getVirtualFile(prefix+names[i]);
    }
    return ret;
  }

  protected void doTest(VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(null, vFile);
    doDoTest(checkWarnings, checkInfos);
  }

  protected @NonNls String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/inspections/";
  }

  protected void doInspectionTest(@NonNls String testFileName,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available
  ) throws Exception {
    doInspectionTest(new String[]{testFileName}, inspectionClass, quickFixName, applyFix, available);
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   * @param testFiles names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName how the resulting fix should be named (the human-readable name users see)
   * @param applyFix true if the fix needs to be applied
   * @param available true if the fix should be available, false if it should be explicitly not available.
   * @throws Exception
   */
  protected void doInspectionTest(@NonNls String[] testFiles,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available
  ) throws Exception {
    final LocalInspectionTool tool = (LocalInspectionTool)inspectionClass.newInstance();
    enableInspectionTool(tool);
    configureByFiles(null, testFiles);
    Collection<HighlightInfo> infos = doDoTest(true, false);

    doQuickFixTest(infos, quickFixName, applyFix, available, testFiles[0]);
    disableInspectionTool(tool.getShortName());
  }

  protected void doQuickFixTest(Collection<HighlightInfo> infos,
                              String quickFixName,
                              boolean applyFix,
                              boolean shouldBeAvailable,
                              String s) throws Exception {
    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>(1);

    TIntObjectHashMap<HighlightInfo> map = new TIntObjectHashMap<HighlightInfo>(infos.size());
    for (HighlightInfo info : infos) {
      final GutterIconRenderer renderer = info.getGutterIconRenderer();
      if (renderer == null) {
        assertFalse("There should be one intention for highlight info", map.containsKey(info.startOffset));
        map.put(info.startOffset, info);

        if (info.quickFixActionRanges != null) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            IntentionAction action = pair.first.getAction();
            availableActions.add(action);
          }
        }
      }
    }

    final IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(availableActions, quickFixName);
    if (shouldBeAvailable) {
      assertNotNull(intentionAction);
    }
    else {
      assertNull(intentionAction);
    }

    if (applyFix && shouldBeAvailable) {
      intentionAction.invoke(myProject, myEditor, myFile);

      checkResultByFile(graftBeforeExt(s, "_after"));
    }
  }

  // Turns "name.ext" to "name_insertion.ext"
  private @NonNls String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) +  insertion + name.substring(dotpos, name.length());
  }

  /*
  @Override
  protected Sdk getProjectJDK() {
    return createMockJdk(PathManager.getHomePath() + "/plugins/python/testData/mockPythonJDK");
  }
  */

  /*
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
  */
}