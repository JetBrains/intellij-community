// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokClassCanBeRecordInspectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true)};
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/lombok/testData";
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  @Override
  public void runSingle() throws Throwable {
    String option = StringUtil.substringAfter(getName(), "__");
    if (option != null && option.startsWith("ignoreConflicts")) {
      // TODO(bartekpacia): It'd be good to test that specific conflicts appear. See IDEA-370463
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(super::runSingle);
    }
    else {
      super.runSingle();
    }
  }
}
