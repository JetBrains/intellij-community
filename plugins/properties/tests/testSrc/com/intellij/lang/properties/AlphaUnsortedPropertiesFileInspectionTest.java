// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.properties.codeInspection.unsorted.AlphaUnsortedPropertiesFileInspection;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;

public class AlphaUnsortedPropertiesFileInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected @NonNls String getBasePath() {
    return "/properties/alphaunsorted";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new AlphaUnsortedPropertiesFileInspection());

    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(getProject());
    final PropertiesCodeStyleSettings propertiesSettings = codeStyleSettings.getCustomSettings(PropertiesCodeStyleSettings.class);

    propertiesSettings.KEEP_BLANK_LINES = true;
  }
}
