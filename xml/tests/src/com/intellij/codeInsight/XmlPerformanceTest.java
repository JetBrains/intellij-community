/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @by Maxim.Mossienko
 */
public class XmlPerformanceTest extends LightQuickFixTestCase {
  private final Set<String> ourTestsWithFolding = new HashSet<>(Arrays.asList("IndentUnindent2"));

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
    myEditor.getSelectionModel().setSelection(0,myEditor.getDocument().getTextLength());

    PlatformTestUtil.startPerformanceTest("indent/unindent "+time, time, () -> {
      EditorActionManager.getInstance().getActionHandler("EditorIndentSelection").execute(myEditor, DataManager.getInstance().getDataContext());

      EditorActionManager.getInstance().getActionHandler("EditorUnindentSelection").execute(myEditor, DataManager.getInstance().getDataContext());
    }).useLegacyScaling().assertTiming();
    final int startOffset = myEditor.getCaretModel().getOffset();
    myEditor.getSelectionModel().setSelection(startOffset,startOffset);
    checkResultByFile(getBasePath() + getTestName(false)+".xml");
  }
}
