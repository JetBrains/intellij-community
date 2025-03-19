// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class HtmlEmmetPerformanceTest extends BasePlatformTestCase {
  public void testPerformance() throws Exception {
    final String fileContent = FileUtil.loadFile(new File(getTestDataPath() + "/performance.html"), StandardCharsets.UTF_8);
    Benchmark.newBenchmark(getTestName(true), () -> {
      for (int i = 0; i < 50; i++) {
        myFixture.configureByText(HtmlFileType.INSTANCE, fileContent);
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(myFixture.getDocument(myFixture.getFile()));
        EditorAction action = (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB);
        //noinspection deprecation
        action.actionPerformed(myFixture.getEditor(), DataManager.getInstance().getDataContext());
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        UIUtil.dispatchAllInvocationEvents();
      }
    }).start();
    myFixture.checkResultByFile("performance_after.html");
  }

  @Override
  @NotNull
  protected String getTestDataPath() {
    return Joiner.on(File.separatorChar).join(XmlTestUtil.getXmlTestDataPath(), "codeInsight", "template", "emmet", "performance");
  }
}
