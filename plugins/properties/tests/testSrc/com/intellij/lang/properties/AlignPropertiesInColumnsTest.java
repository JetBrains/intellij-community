package com.intellij.lang.properties;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AlignPropertiesInColumnsTest extends LightQuickFixParameterizedTestCase {

  private PropertiesCodeStyleSettings myPropertiesSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(getProject());
    myPropertiesSettings = codeStyleSettings.getCustomSettings(PropertiesCodeStyleSettings.class);

    codeStyleSettings.getCommonSettings(PropertiesLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    myPropertiesSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = true;
  }

  @Override
  protected @NonNls String getBasePath() {
    return "/properties/align_in_columns";
  }

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    myPropertiesSettings.KEY_VALUE_DELIMITER_CODE = actionHint.getExpectedText().charAt(0);

    executeAction("ReformatCode");

    checkResult(testName);
  }

  private void checkResult(@NotNull final String testName) {
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file: " + expectedFilePath, expectedFilePath, false);
  }
}
