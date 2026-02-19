// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack.lang;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.youtrack.YouTrackIntellisense;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.tasks.youtrack.YouTrackIntellisense.HighlightRange;
import static com.intellij.tasks.youtrack.lang.YouTrackHighlightingAnnotator.QueryInfo;

/**
 * @author Mikhail Golubev
 */
public class YouTrackHighlightingAnnotator extends ExternalAnnotator<QueryInfo, List<HighlightRange>> {
  private static final Logger LOG = Logger.getInstance(YouTrackHighlightingAnnotator.class);

  @Override
  public @Nullable QueryInfo collectInformation(@NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
    final YouTrackIntellisense intellisense = psiFile.getUserData(YouTrackIntellisense.INTELLISENSE_KEY);
    if (intellisense == null || !intellisense.getRepository().isConfigured()) {
      return null;
    }
    final String text = psiFile.getText();
    final int offset = editor.getCaretModel().getOffset();
    //LOG.debug(String.format("Highlighting YouTrack query: '%s' (cursor=%d)", text, offset));
    return new QueryInfo(offset, text, intellisense);
  }

  @Override
  public @Nullable List<HighlightRange> doAnnotate(QueryInfo collectedInfo) {
    if (collectedInfo == null) {
      return Collections.emptyList();
    }
    final String query = collectedInfo.myText;
    final int offset = collectedInfo.myCaretOffset;
    try {
      return collectedInfo.myIntellisense.fetchHighlighting(query, offset);
    }
    catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public void apply(@NotNull PsiFile psiFile, List<HighlightRange> ranges, @NotNull AnnotationHolder holder) {
    for (HighlightRange range : ranges) {
      if (range.getStyleClass().equals("error")) {
        holder.newSilentAnnotation(HighlightSeverity.ERROR).range(range.getTextRange()).create();
      }
      else {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range.getTextRange())
        .enforcedTextAttributes(range.getTextAttributes()).create();
      }
    }
  }

  public static final class QueryInfo {
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
