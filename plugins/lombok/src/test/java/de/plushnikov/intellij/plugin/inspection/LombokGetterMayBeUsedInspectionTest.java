// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokGetterMayBeUsedInspectionTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LombokGetterMayBeUsedInspection()};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/lombok/testData/inspection/lombokGetterMayBeUsed/";
  }

  private void doTest() {
    doTest(getTestName(false) + ".java", true, false);
  }

  public void testFieldsWithGetter() {
    doTest();
  }

  public void testInstanceAndStaticFields() {
    doTest();
  }

  public void testShortMethods() {
    doTest();
  }

  public void testRecord() {
    doTest();
  }

  public void testCompactSourceFile() {
    doTest();
  }
}
