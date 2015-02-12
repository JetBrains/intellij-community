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
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author spleaner
 */
@SuppressWarnings({"ALL"})
public class XmlMoverTest extends LightPlatformCodeInsightTestCase {

  public void testTag() throws Exception { doTest("xml"); }
  public void testTag2() throws Exception { doTest("xml"); }
  public void testTag3() throws Exception { doTest("xml"); }
  public void testTag4() throws Exception { doTest("xml"); }
  public void testTag5() throws Exception { doTest("xml"); }
  public void testTag6() throws Exception { doTest("xml"); }
  public void testTag7() throws Exception { doTest("xml"); }
  public void testTag8() throws Exception { doTest("xml"); }
  public void testTag9() throws Exception { doTest("xml"); }
  public void testTag10() throws Exception { doTest("xml"); }
  public void testTag11() throws Exception { doTest("xml"); }

  public void testMultiLineTag() throws Exception { doTest("xml"); }

  public void testRootTag() throws Exception { doTest("xml"); }
  public void testRootTag1() throws Exception { doTest("xml"); }
  public void testSingleTag() throws Exception { doTest("xml"); }
  public void testMoveTag() throws Exception { doTest("xml"); }
  public void testMoveTag1() throws Exception { doTest("xml"); }
  public void testMoveTagWithEmptyLines() throws Exception { doTest("xml"); }

  public void test1() throws Exception { doTest("html"); }


  private void doTest(String ext) throws Exception {
    final String baseName = getBasePath() + '/' + getTestName(true);
    final String fileName = baseName + "."+ext;

    @NonNls String afterFileName = baseName + "_afterUp." + ext;
    EditorActionHandler handler = new MoveStatementUpAction().getHandler();
    performAction(fileName, handler, afterFileName);

    afterFileName = baseName + "_afterDown." + ext;
    handler = new MoveStatementDownAction().getHandler();
    performAction(fileName, handler, afterFileName);
  }

  private void performAction(final String fileName, final EditorActionHandler handler, final String afterFileName) throws Exception {
    configureByFile(fileName);
    final boolean enabled = handler.isEnabled(myEditor, null);
    assertEquals("not enabled for " + afterFileName, new File(getTestDataPath(), afterFileName).exists(), enabled);
    if (enabled) {
      WriteCommandAction.runWriteCommandAction(null, new Runnable() {
        @Override
        public void run() {
          handler.execute(myEditor, null);
        }
      });
      checkResultByFile(afterFileName);
    }
  }

  protected String getBasePath() {
    return "/mover";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData";
  }
}