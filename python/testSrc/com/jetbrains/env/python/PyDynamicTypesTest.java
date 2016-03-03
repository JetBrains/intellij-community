/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env.python;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.debugger.PySignatureCacheManagerImpl;

import java.io.IOException;

/**
 * @author traff
 */

public class PyDynamicTypesTest extends PyEnvTestCase {
  public void test1() throws Exception {
    doTest(getTestName(true) + ".py");
  }

  private void doTest(final String scriptName) {
    runPythonTest(new PyDebuggerTask("/" + "dynamicTypes", scriptName) {
      @Override
      public void setUp(String testName) throws Exception {
        super.setUp(testName);
      }

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(myFixture.getProject()).setSaveCallSignatures(true);
      }

      public void doFinally() {
        try {
          PySignatureCacheManagerImpl.CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(getVirtualFile(), "".getBytes());
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

        edt(() -> {
          myFixture.configureByFile("dynamicTypes/" + scriptName);

          try {
            //copy signature attributes from real file to temporary test file
            byte[] bytes = PySignatureCacheManagerImpl.CALL_SIGNATURES_ATTRIBUTE
              .readAttributeBytes(getVirtualFile());
            PySignatureCacheManagerImpl.CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(myFixture.getFile().getVirtualFile(),
                                                                                      bytes);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }

          EditorTestUtil.setCaretsAndSelection(myFixture.getEditor(), new EditorTestUtil.CaretAndSelectionState(
            Lists.newArrayList(new EditorTestUtil.CaretInfo(new LogicalPosition(0, 6), null)), null));
          final IntentionAction action = myFixture.findSingleIntention(PyBundle.message("INTN.doc.string.stub"));
          myFixture.launchAction(action);
          myFixture.checkResultByFile("dynamicTypes/" + getTestName(true) + "_after.py");
        });
      }
    });
  }
}

