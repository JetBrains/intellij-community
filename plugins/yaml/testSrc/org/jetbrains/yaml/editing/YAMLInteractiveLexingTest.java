// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.editing;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/** Test interactive lexing in highlight while typing */
public class YAMLInteractiveLexingTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/editing/data/";
  }

  public void testChangeOfBlockScalarValueInInlineMap() {
    setUpFile();
    String firstPass = tokenList();
    myFixture.type("n");
    String secondPass = tokenList();
    assertEquals(firstPass, secondPass);
  }

  private void setUpFile() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
  }

  @NotNull
  private String tokenList() {
    Editor editor = myFixture.getEditor();
    assert editor instanceof EditorEx : "Unsupported editor type";

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    StringBuilder result = new StringBuilder();

    for (HighlighterIterator it = highlighter.createIterator(0); !it.atEnd(); it.advance()) {
      IElementType type = it.getTokenType();
      result.append(type.toString());
      result.append('\n');
    }
    return result.toString();
  }
}
