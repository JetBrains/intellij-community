package com.jetbrains.edu;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class EduAnswerPlaceholderPainter {
  private EduAnswerPlaceholderPainter() {

  }

  public static void drawAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder placeholder,
                                           boolean useLength, @NotNull final JBColor color) {
    Document document = editor.getDocument();
    if (useLength && !placeholder.isValid(document)) {
      return;
    }
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes defaultTestAttributes = new TextAttributes(scheme.getDefaultForeground(), scheme.getDefaultBackground(), null,
                                                                    EffectType.BOXED, Font.PLAIN);
    final Project project = editor.getProject();
    assert project != null;
    int startOffset = placeholder.getRealStartOffset(document);
    final int length = placeholder.getLength();
    final int replacementLength = placeholder.getPossibleAnswerLength();
    int highlighterLength = useLength ? length : replacementLength;
    int endOffset = startOffset + highlighterLength;
    RangeHighlighter
      highlighter = editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST + 1,
                                                                defaultTestAttributes, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setCustomRenderer(new CustomHighlighterRenderer() {
      @Override
      public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
        g.setColor(color);
        Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getStartOffset()));
        Point pointEnd = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getEndOffset()));
        g.drawRect(point.x, point.y - 2, (pointEnd.x - point.x), editor.getLineHeight() + 1);
      }
    });
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
  }

  public static void createGuardedBlock(Editor editor, List<RangeMarker> blocks, int start, int end) {
    RangeHighlighter rh = editor.getMarkupModel()
      .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, null, HighlighterTargetArea.EXACT_RANGE);
    blocks.add(rh);
  }


  public static void createGuardedBlocks(@NotNull final Editor editor, TaskFile taskFile, boolean useLength) {
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      createGuardedBlocks(editor, answerPlaceholder, useLength);
    }
  }

  public static void createGuardedBlocks(@NotNull final Editor editor, AnswerPlaceholder placeholder, boolean useLength) {
    Document document = editor.getDocument();
    if (document instanceof DocumentImpl) {
      DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      if (useLength && !placeholder.isValid(document)) return;
      int start = placeholder.getRealStartOffset(document);
      final int length = useLength ? placeholder.getLength() : placeholder.getPossibleAnswerLength();
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
