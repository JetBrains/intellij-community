// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.ThrowableRunnable;
import junit.framework.TestSuite;
import org.intellij.lang.annotations.Language;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@SuppressWarnings("unused")
public class MarkdownHeaderTestSuite extends TestSuite {
  public static final class RunMarkdownHeaderUpTestSuite {
    public static TestSuite suite() {
      return new MarkdownHeaderUpTestSuite();
    }
  }

  public static final class RunMarkdownHeaderDownTestSuite {
    public static TestSuite suite() {
      return new MarkdownHeaderDownTestSuite();
    }
  }

  public MarkdownHeaderTestSuite(@Language("devkit-action-id") @NotNull String actionId, @NotNull String dataName) {
    String testDataPath = getHeadersTestData();
    File dir = new File(testDataPath);
    File[] files = dir.listFiles((dir1, name) -> name.endsWith("_before.md"));
    for (File testFile : files) {
      addTest(new LightPlatformCodeInsightTestCase() {
        @Override
        public String getName() {
          return "test_" + testFile.getName();
        }

        @Override
        protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
          configureByFile(testFile.getName());
          final var editor = getEditor();
          CommandProcessor.getInstance().executeCommand(
            getProject(),
            () -> EditorTestUtil.executeAction(editor, actionId, false),
            "",
            null,
            editor.getDocument()
          );
          checkResultByFile(dataName + "/" + StringUtil.substringBefore(testFile.getName(), "_before.md") + "_after.md");
        }

        @NotNull
        @Override
        protected String getTestDataPath() {
          return testDataPath;
        }
      });
    }
  }

  @Override
  public String getName() {
    return "MarkdownHeaderTest";
  }

  @NotNull
  private static String getHeadersTestData() {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/headers/";
  }

  private static class MarkdownHeaderUpTestSuite extends MarkdownHeaderTestSuite {
    MarkdownHeaderUpTestSuite() {
      super("org.intellij.plugins.markdown.ui.actions.styling.HeaderUpAction", "headerUp");
    }
  }

  private static class MarkdownHeaderDownTestSuite extends MarkdownHeaderTestSuite {
    MarkdownHeaderDownTestSuite() {
      super("org.intellij.plugins.markdown.ui.actions.styling.HeaderDownAction", "headerDown");
    }
  }
}