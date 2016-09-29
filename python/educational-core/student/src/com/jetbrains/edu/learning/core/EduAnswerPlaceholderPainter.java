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
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

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
    if (placeholder.isActive()) {
      drawAnswerPlaceholder(editor, placeholder, textAttributes, PLACEHOLDERS_LAYER);
    } else if (!placeholder.getUseLength()) {
      int offset = placeholder.getOffset();
      drawAnswerPlaceholderFromPrevStep(editor, offset, offset + placeholder.getVisibleLength(placeholder.getActiveSubtaskIndex()));
    }
  }

  public static void drawAnswerPlaceholder(@NotNull Editor editor,
                                           @NotNull AnswerPlaceholder placeholder,
                                           TextAttributes textAttributes,
                                           int placeholdersLayer) {
    final int startOffset = placeholder.getOffset();
    if (startOffset == - 1) {
      return;
    }
    final int length = placeholder.getRealLength();
    final int endOffset = startOffset + length;
    drawAnswerPlaceholder(editor, startOffset, endOffset, textAttributes, placeholdersLayer);
  }

  public static void drawAnswerPlaceholder(@NotNull Editor editor,
                                           int start,
                                           int end,
                                           TextAttributes textAttributes,
                                           int placeholdersLayer) {
    final Project project = editor.getProject();
    assert project != null;
    if (start == - 1) {
      return;
    }
    RangeHighlighter
      highlighter = editor.getMarkupModel().addRangeHighlighter(start, end, placeholdersLayer,
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
    for (AnswerPlaceholder answerPlaceholder : taskFile.getActivePlaceholders()) {
      createGuardedBlocks(editor, answerPlaceholder);
    }
  }

  public static void createGuardedBlocks(@NotNull final Editor editor, AnswerPlaceholder placeholder) {
    Document document = editor.getDocument();
    if (document instanceof DocumentImpl) {
      DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      int start = placeholder.getOffset();
      final int length = placeholder.getRealLength();
      int end = start + length;
      if (start != 0) {
        createGuardedBlock(editor, blocks, start - 1, start);
      }
      if (end != document.getTextLength()) {
        createGuardedBlock(editor, blocks, end, end + 1);
      }
    }
  }

}
