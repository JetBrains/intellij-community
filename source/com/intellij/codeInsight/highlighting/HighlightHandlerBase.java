package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.psi.*;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;

/**
 * Author: msk
 */
public abstract class HighlightHandlerBase {

  protected final void setupFindModel(final Project project) {
    final FindManager findManager = FindManager.getInstance(project);
    FindModel model = findManager.getFindNextModel();
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model.setSearchHighlighters(true);
    findManager.setFindWasPerformed();
    findManager.setFindNextModel(model);
  }

  protected final void setLineTextErrorStripeTooltip(final Document document, final int offset, final RangeHighlighter highlighter) {
    final int lineNumber = document.getLineNumber(offset);
    final String lineText = document.getText().substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
    highlighter.setErrorStripeTooltip("  " + lineText.trim () + "  ");
  }
}
