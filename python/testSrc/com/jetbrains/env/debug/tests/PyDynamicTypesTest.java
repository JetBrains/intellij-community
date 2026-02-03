// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.common.EditorCaretTestUtil;
import com.jetbrains.env.EnvTestTagsRequired;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyDebuggerTask;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.debugger.PySignatureCacheManagerImpl;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PyDynamicTypesTest extends PyEnvTestCase {

  @EnvTestTagsRequired(tags = {}, skipOnFlavors = {})
  @Test
  public void test1() {
    doTest(getTestName(true) + ".py");
  }

  private void doTest(final String scriptName) {
    runPythonTest(new PyDebuggerTask("/" + "dynamicTypes", scriptName) {

      @Override
      public void before() {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setSaveCallSignatures(true);
      }

      @Override
      public void doFinally() {
        try {
          PySignatureCacheManagerImpl.CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(getVirtualFile(), "".getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
          //pass
        }
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setSaveCallSignatures(false);
      }

      private VirtualFile getVirtualFile() {
        return LocalFileSystem.getInstance()
          .refreshAndFindFileByPath(
            getTestDataPath() +
            "/dynamicTypes/" +
            scriptName);
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        EdtTestUtil.runInEdtAndWait(() -> {
          myFixture.configureByFile(scriptName);

          EditorTestUtil.setCaretsAndSelection(myFixture.getEditor(), new EditorCaretTestUtil.CaretAndSelectionState(
            Lists.newArrayList(new EditorCaretTestUtil.CaretInfo(new LogicalPosition(0, 6), null)), null));
          final IntentionAction action = myFixture.findSingleIntention(PyPsiBundle.message("INTN.insert.docstring.stub"));
          boolean saved = PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB;
          try {
            PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB = true;
            myFixture.launchAction(action);
            myFixture.checkResultByFile("dynamicTypes/" + getTestName(true) + "_after.py");
          } finally {
            PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB = saved;
          }
          });
      }
    });
  }
}

