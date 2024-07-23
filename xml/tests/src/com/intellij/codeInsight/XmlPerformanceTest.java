// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class XmlPerformanceTest extends LightQuickFixTestCase {
  private static final Set<String> ourTestsWithFolding = Set.of("IndentUnindent2");

  @Override
  protected String getBasePath() {
    return "performance/";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }

  public void testDummy() {
    @SuppressWarnings({"UnusedDeclaration"})
    Class clazz = IdeaTestUtil.class;
  }

  public void testIndentUnindent() {
    doIndentTest(2000);
  }

  public void testIndentUnindent2() {
    doIndentTest(2001);
  }

  @Override
  protected boolean doFolding() {
    return ourTestsWithFolding.contains(getTestName(false));
  }

  private void doIndentTest(int time) {
    configureByFile(getBasePath() + getTestName(false)+".xml");
    doHighlighting();
    getEditor().getSelectionModel().setSelection(0, getEditor().getDocument().getTextLength());

    Benchmark.newBenchmark("indent/unindent " + time, () -> {
      EditorActionManager.getInstance().getActionHandler("EditorIndentSelection").execute(getEditor(), null,
                                                                                          DataManager.getInstance().getDataContext());

      EditorActionManager.getInstance().getActionHandler("EditorUnindentSelection").execute(getEditor(), null,
                                                                                            DataManager.getInstance().getDataContext());
    }).start();
    final int startOffset = getEditor().getCaretModel().getOffset();
    getEditor().getSelectionModel().setSelection(startOffset, startOffset);
    checkResultByFile(getBasePath() + getTestName(false)+".xml");
  }
}
