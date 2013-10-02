package com.intellij.tasks.youtrack.lang;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.youtrack.YouTrackIntellisense;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.tasks.youtrack.YouTrackIntellisense.HighlightRange;
import static com.intellij.tasks.youtrack.lang.YouTrackHighlightingAnnotator.QueryInfo;

/**
 * @author Mikhail Golubev
 */
public class YouTrackHighlightingAnnotator extends ExternalAnnotator<QueryInfo, List<HighlightRange>> {
  private static Logger LOG = Logger.getInstance(YouTrackHighlightingAnnotator.class);

  private static final boolean DEBUG = false;
  private static final TextAttributes DEBUG_ATTRIBUTES =
    new TextAttributes(JBColor.RED, JBColor.WHITE, JBColor.GREEN, EffectType.STRIKEOUT, Font.BOLD);

  @Nullable
  @Override
  public QueryInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    if (DEBUG) {
      return new QueryInfo(0, "empty", null);
    }
    final YouTrackIntellisense intellisense = file.getUserData(YouTrackIntellisense.INTELLISENSE_KEY);
    if (intellisense == null) {
      return null;
    }
    final String text = file.getText();
    final int offset = editor.getCaretModel().getOffset();
    //LOG.debug(String.format("Highlighting YouTrack query: '%s' (cursor=%d)", text, offset));
    return new QueryInfo(offset, text, intellisense);
  }

  @Nullable
  @Override
  public List<HighlightRange> doAnnotate(QueryInfo collectedInfo) {
    //noinspection ConstantConditions
    if (collectedInfo == null || DEBUG) {
      return Collections.emptyList();
    }
    String query = collectedInfo.myText;
    int offset = collectedInfo.myCaretOffset;
    try {
      return collectedInfo.myIntellisense.fetchHighlighting(query, offset);
    }
    catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public void apply(@NotNull PsiFile file, List<HighlightRange> ranges, @NotNull AnnotationHolder holder) {
    if (DEBUG) {
      Annotation annotation = holder.createErrorAnnotation(file.getFirstChild(), "Debug message");
      annotation.setEnforcedTextAttributes(DEBUG_ATTRIBUTES);
      return;
    }
    for (HighlightRange range : ranges) {
      if (range.getStyleClass().equals("error")) {
        holder.createErrorAnnotation(range.getTextRange(), null);
      }
      else {
        final Annotation infoAnnotation = holder.createInfoAnnotation(range.getTextRange(), null);
        infoAnnotation.setEnforcedTextAttributes(range.getTextAttributes());
      }
    }
  }

  public static class QueryInfo {
    private final int myCaretOffset;
    private final String myText;
    private final YouTrackIntellisense myIntellisense;

    private QueryInfo(int caretOffset, String text, YouTrackIntellisense repository) {
      myCaretOffset = caretOffset;
      myText = text;
      myIntellisense = repository;
    }
  }
}
