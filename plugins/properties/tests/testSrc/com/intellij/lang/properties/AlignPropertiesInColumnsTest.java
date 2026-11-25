package com.intellij.lang.properties;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class AlignPropertiesInColumnsTest {
  private static final String TEST_DATA_PATH = PropertiesQuickFixTestUtil.getPropertiesTestDataRoot() + "/properties/align_in_columns";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  private PropertiesCodeStyleSettings myPropertiesSettings;

  @BeforeEach
  void setUpSettings() {
    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(quickFixFixture.get().getFixture().getProject());
    myPropertiesSettings = codeStyleSettings.getCustomSettings(PropertiesCodeStyleSettings.class);

    codeStyleSettings.getCommonSettings(PropertiesLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    myPropertiesSettings.SPACES_AROUND_KEY_VALUE_DELIMITER = true;
  }

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    ActionHint actionHint = quickFix.configure(fileName);
    myPropertiesSettings.KEY_VALUE_DELIMITER_CODE = actionHint.getExpectedText().charAt(0);

    PropertiesQuickFixTestUtil.executeAction(quickFix.getFixture(), "ReformatCode");

    checkResult(fileName);
  }

  private void checkResult(@NotNull final String fileName) {
    quickFixFixture.get().getFixture().checkResultByFile("after" + fileName);
  }
}
