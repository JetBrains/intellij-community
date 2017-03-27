package com.jetbrains.edu.learning.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class EduAnswerPlaceholderPainter {

  //it should be the lowest highlighting layer, otherwise selection and other effects are not visible
  public static final int PLACEHOLDERS_LAYER = 0;

  private EduAnswerPlaceholderPainter() {

  }

  public static void drawAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder placeholder,
                                           @NotNull final JBColor color) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes textAttributes = new TextAttributes(scheme.getDefaultForeground(), scheme.getDefaultBackground(), null,
                                                             EffectType.BOXED, Font.PLAIN);
    textAttributes.setEffectColor(color);
    int startOffset = placeholder.getOffset();
    if (startOffset == -1) {
      return;
    }
    final int length =
      placeholder.isActive() ? placeholder.getRealLength() : placeholder.getVisibleLength(placeholder.getActiveSubtaskIndex());
    Pair<Integer, Integer> offsets = StudyUtils.getPlaceholderOffsets(placeholder, editor.getDocument());
    startOffset = offsets.first;
    int endOffset = offsets.second;
    if (placeholder.isActive()) {
      drawAnswerPlaceholder(editor, startOffset, endOffset, textAttributes, PLACEHOLDERS_LAYER);
    }
    else if (!placeholder.getUseLength() && length != 0) {
      drawAnswerPlaceholderFromPrevStep(editor, startOffset, endOffset);
    }
  }

  public static void drawAnswerPlaceholder(@NotNull Editor editor,
                                           int start,
                                           int end,
                                           @Nullable TextAttributes textAttributes,
                                           int placeholdersLayer) {
    final Project project = editor.getProject();
    assert project != null;
    if (start == -1) {
      return;
    }
    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(start, end, placeholdersLayer,
                                                                               textAttributes, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
  }


  public static void drawAnswerPlaceholderFromPrevStep(@NotNull Editor editor, int start, int end) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(EditorColors.TEARLINE_COLOR);
    SimpleTextAttributes attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
    final TextAttributes textAttributes = new TextAttributes(attributes.getFgColor(), color, null,
                                                             null, attributes.getFontStyle());

    drawAnswerPlaceholder(editor, start, end, textAttributes, HighlighterLayer.LAST);
  }

  public static void createGuardedBlock(Editor editor, List<RangeMarker> blocks, int start, int end) {
    RangeHighlighter rh = editor.getMarkupModel()
      .addRangeHighlighter(start, end, PLACEHOLDERS_LAYER, null, HighlighterTargetArea.EXACT_RANGE);
    blocks.add(rh);
  }


  public static void createGuardedBlocks(@NotNull final Editor editor, TaskFile taskFile) {
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      createGuardedBlocks(editor, answerPlaceholder);
    }
  }

  public static void createGuardedBlocks(@NotNull final Editor editor, AnswerPlaceholder placeholder) {
    Document document = editor.getDocument();
    if (document instanceof DocumentImpl) {
      DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      Pair<Integer, Integer> offsets = StudyUtils.getPlaceholderOffsets(placeholder, editor.getDocument());
      Integer start = offsets.first;
      Integer end = offsets.second;
      if (start != 0) {
        createGuardedBlock(editor, blocks, start - 1, start);
      }
      if (end != document.getTextLength()) {
        createGuardedBlock(editor, blocks, end, end + 1);
      }
    }
  }
}
