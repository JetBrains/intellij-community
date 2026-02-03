// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import org.jetbrains.annotations.NotNull;

public class ReplaceTagEmptyBodyTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new CheckTagEmptyBodyInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/quickFix/replaceTagEmptyBodyWithEmptyEnd";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }
}