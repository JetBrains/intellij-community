package com.intellij.testFramework;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author yole
 */
public abstract class InspectionFixtureTestCase extends CodeInsightFixtureTestCase {
  public void doTest(@NonNls String folderName, LocalInspectionTool tool) throws Exception {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }

  public void doTest(@NonNls String folderName, InspectionTool tool) throws Exception {
    final String testDir = getTestDataPath() + "/" + folderName;
    runTool(folderName, tool);

    InspectionTestUtil.compareToolResults(tool, false, testDir);
  }

  private void runTool(String testDir, InspectionTool tool) throws IOException {
    VirtualFile sourceDir = myFixture.copyDirectoryToProject(testDir, "");
    AnalysisScope scope = new AnalysisScope(myFixture.getPsiManager().findDirectory(sourceDir));

    InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(myFixture.getProject());
    final GlobalInspectionContextImpl globalContext = inspectionManager.createNewGlobalContext(true);
    globalContext.setCurrentScope(scope);

    InspectionTestUtil.runTool(tool, scope, globalContext, inspectionManager);
  }
}
