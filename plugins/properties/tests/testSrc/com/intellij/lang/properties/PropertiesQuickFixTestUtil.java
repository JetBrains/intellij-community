// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime;

/**
 * Helpers of the quick fix tests of the properties plugin. Their test data is under the test data of `intellij.java.tests`.
 */
final class PropertiesQuickFixTestUtil {
  @NonNls
  private static final String JAVA_TEST_DATA_PATH = "java/java-tests/testData";

  private PropertiesQuickFixTestUtil() {
  }

  /**
   * Returns the test data root of `intellij.java.tests`, under Bazel from the runfiles.
   */
  static @NotNull @NonNls String getPropertiesTestDataRoot() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//java/java-tests:testData");
      return getFileFromBazelRuntime(label).toAbsolutePath().toString();
    }
    return PathManagerEx.getCommunityHomePath() + "/" + JAVA_TEST_DATA_PATH;
  }

  /**
   * Runs the editor action {@code actionId} in the editor of {@code fixture}, on the EDT and in a command.
   * The action must be enabled.
   */
  static void executeAction(@NotNull JavaCodeInsightTestFixture fixture, @NonNls @NotNull String actionId) {
    EdtTestUtil.runInEdtAndWait(() -> {
      Editor editor = fixture.getEditor();
      CommandProcessor.getInstance()
        .executeCommand(fixture.getProject(), () -> EditorTestUtil.executeAction(editor, actionId, true), "", null, editor.getDocument());
    });
  }
}
