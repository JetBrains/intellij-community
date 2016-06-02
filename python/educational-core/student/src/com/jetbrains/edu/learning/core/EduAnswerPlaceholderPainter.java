package com.jetbrains.edu.learning.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
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
    final Document document = editor.getDocument();
    if (!placeholder.isValid(document)) {
      return;
    }
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes textAttributes = new TextAttributes(scheme.getDefaultForeground(), scheme.getDefaultBackground(), null,
                                                                    EffectType.BOXED, Font.PLAIN);
    final Project project = editor.getProject();
    assert project != null;
    final int startOffset = placeholder.getRealStartOffset(document);
    final int length = placeholder.getRealLength();
    final int endOffset = startOffset + length;
    textAttributes.setEffectColor(color);
    RangeHighlighter
      highlighter = editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, PLACEHOLDERS_LAYER,
                                                                textAttributes, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
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
      if (!placeholder.isValid(document)) return;
      int start = placeholder.getRealStartOffset(document);
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
