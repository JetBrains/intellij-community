// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.resolve;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class YAMLUsagesHighlighterTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/resolve/data/";
  }

  public void testAnchorsAndAliases() {
    doTest();
  }

  // TODO: It is copy-paste from puppet plug-in. Extract it to common utility class!
  private void doTest() {
    String testName = getTestName(true);
    RangeHighlighter[] ranges = myFixture.testHighlightUsages(testName + ".yml");

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes readAttributes = scheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    TextAttributes writeAttributes = scheme.getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
    StringBuilder result = new StringBuilder();

    Editor editor = myFixture.getEditor();

    List<RangeHighlighter> highlighters = Arrays.asList(ranges);
    ContainerUtil.sort(highlighters, Comparator.comparingInt(RangeMarker::getStartOffset));

    for (RangeHighlighter highlighter : highlighters) {
      String usageType;
      TextAttributes attributes = highlighter.getTextAttributes();

      if (attributes == null) {
        continue;
      }
      else if (attributes.equals(readAttributes)) {
        usageType = "read usage";
      }
      else if (attributes.equals(writeAttributes)) {
        usageType = "write usage";
      }
      else {
        continue;
      }

      TextRange range = TextRange.create(highlighter.getStartOffset(), highlighter.getEndOffset());
      CharSequence text = highlighter.getDocument().getCharsSequence();
      result
        .append(editor.offsetToLogicalPosition(range.getStartOffset()))
        .append(" - '")
        .append(range.subSequence(text))
        .append("': ")
        .append(usageType)
        .append("\n")
      ;
    }

    assertSameLinesWithFile(getTestDataPath() + testName + ".highlight.txt", result.toString());
  }
}
