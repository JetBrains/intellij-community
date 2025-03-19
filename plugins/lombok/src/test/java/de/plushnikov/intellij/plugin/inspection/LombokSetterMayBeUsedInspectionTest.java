// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokSetterMayBeUsedInspectionTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LombokSetterMayBeUsedInspection()};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/lombok/testData";
  }

  @NotNull
  private String getFilePath() {
    return "/inspection/lombokSetterMayBeUsed/" + getTestName(false) + ".java";
  }

  private void doTest() {
    doTest(getFilePath(), true, false);
  }

  public void testFieldsWithSetter() {
    doTest();
  }

  public void testInstanceAndStaticFields() {
    doTest();
  }

  public void testSetterAlreadyUsed() {
    doTest();
  }

  public void testSetterAlreadyUsedTolerate() {
    doTest();
  }

  public void testSetterOnBooleanIsPrefixedField() {
    doTest();
  }
}
