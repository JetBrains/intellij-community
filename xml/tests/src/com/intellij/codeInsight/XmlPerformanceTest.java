// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

/**
 * @author Maxim.Mossienko
 */
@PerformanceUnitTest
@StressTestApplication
public class XmlPerformanceTest {
  private static final Set<String> ourTestsWithFolding = Set.of("IndentUnindent2");
  private static final String BASE_PATH = "performance/";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, List::of, LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/xml/tests/testData/"));

  @BeforeEach
  void setUp() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    if (ourTestsWithFolding.contains(daemon.getFixture().getTestName(false))) {
      daemon.setOptions(daemon.getOptions().withFolding());
    }
  }

  @Test
  public void testDummy() {
    @SuppressWarnings({"UnusedDeclaration"})
    Class clazz = IdeaTestUtil.class;
  }

  @Test
  public void testIndentUnindent() {
    doIndentTest(2000);
  }

  @Test
  public void testIndentUnindent2() {
    doIndentTest(2001);
  }

  private void doIndentTest(int time) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".xml");
    daemon.doHighlighting();
    Editor editor = myFixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength()));

    String launchName = "indent/unindent " + time;
    String fullTestName = XmlPerformanceTest.class.getName() + ".test" + myFixture.getTestName(false) + " - " + launchName;
    myFixture.runInCommand(() -> {
      Benchmark.newBenchmark(launchName, () -> {
        EditorActionManager.getInstance().getActionHandler("EditorIndentSelection").execute(editor, null,
                                                                                            DataManager.getInstance().getDataContext());

        EditorActionManager.getInstance().getActionHandler("EditorUnindentSelection").execute(editor, null,
                                                                                              DataManager.getInstance().getDataContext());
      }).start(fullTestName);
      final int startOffset = editor.getCaretModel().getOffset();
      editor.getSelectionModel().setSelection(startOffset, startOffset);
    });
    myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + ".xml");
  }
}
