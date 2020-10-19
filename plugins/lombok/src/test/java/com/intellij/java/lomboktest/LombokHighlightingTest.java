package com.intellij.java.lomboktest;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.siyeh.ig.bugs.ObjectEqualityInspection;
import com.siyeh.ig.bugs.ObjectToStringInspection;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LombokHighlightingTest extends LightDaemonAnalyzerTestCase {


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection(true));
    setWarningLevel(new ObjectEqualityInspection());
  }

  private void setWarningLevel(LocalInspectionTool inspection) {
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(inspection.getShortName());
    final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(getProject()).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, getProject());
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ObjectToStringInspection(),
      new ObjectEqualityInspection(),
      new DataFlowInspection(),
      new DefUseInspection(),
      new FieldMayBeFinalInspection(),
      new FieldCanBeLocalInspection()
    };
  }

  public void testLombokBasics() { doTest(); }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }

  private void doTest() {
    doTest("/plugins/lombok/testData/highlighting/" + getTestName(false) + ".java", true, false);
  }

  @Override
  protected @NonNls @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath();
  }
}
