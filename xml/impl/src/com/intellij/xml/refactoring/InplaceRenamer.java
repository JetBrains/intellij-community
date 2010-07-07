/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xml.refactoring;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class InplaceRenamer {

  @NonNls protected static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls protected static final String OTHER_VARIABLE_NAME = "OtherVariable";

  protected final static Stack<InplaceRenamer> ourRenamersStack = new Stack<InplaceRenamer>();

  protected final Editor myEditor;
  protected final ArrayList<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();


  public InplaceRenamer(@NotNull Editor editor) {
    myEditor = editor;
  }

  protected void addHighlights(List<TextRange> ranges) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    final HighlightManager highlightManager = HighlightManager.getInstance(myEditor.getProject());
    for (final TextRange range : ranges) {
      highlightManager.addOccurrenceHighlight(myEditor, range.getStartOffset(), range.getEndOffset(), attributes, 0, myHighlighters, null);
    }

    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  public static void rename(InplaceRenamer renamer) {
    if (!ourRenamersStack.isEmpty()) {
      ourRenamersStack.peek().finish();
    }

    ourRenamersStack.push(renamer);
    renamer.rename();
  }

  protected void finish() {
    ourRenamersStack.pop();

    final HighlightManager highlightManager = HighlightManager.getInstance(myEditor.getProject());
    for (final RangeHighlighter highlighter : myHighlighters) {
      highlightManager.removeSegmentHighlighter(myEditor, highlighter);
    }
  }

  protected abstract void rename();
}
