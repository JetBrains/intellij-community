// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlExtension.AttributeValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.xml.util.HtmlUtil.hasHtml;

public class XmlEqTypedHandler extends TypedHandlerDelegate {

  private static final Key<QuoteInfo> QUOTE_INSERTED_AT = new Key<>("xml.eq-handler.inserted-quote");

  private boolean needToInsertQuotes = false;

  @Override
  public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    var currentCaret = editor.getCaretModel().getCurrentCaret();
    var quoteInsertedAt = currentCaret.getUserData(QUOTE_INSERTED_AT);
    if (quoteInsertedAt != null) {
      currentCaret.putUserData(QUOTE_INSERTED_AT, null);
    }
    if ((c == '"' || (c == '\'' && hasHtml(file)))
        && quoteInsertedAt != null
        && quoteInsertedAt.position == currentCaret.getOffset()
        && quoteInsertedAt.quote != '{') {
      return Result.STOP;
    }
    if (c == '=' && WebEditorOptions.getInstance().isInsertQuotesForAttributeValue()) {
      if (XmlGtTypedHandler.fileContainsXmlLanguage(file)) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

        PsiElement atParent = getAttributeCandidate(editor, file, false);
        if (atParent instanceof XmlAttribute && ((XmlAttribute)atParent).getValueElement() == null) {
          needToInsertQuotes = ((XmlAttribute)atParent).getValueElement() == null;
        }
      }
    }

    return super.beforeCharTyped(c, project, editor, file, fileType);
  }

  private static @Nullable PsiElement getAttributeCandidate(@NotNull Editor editor, @NotNull PsiFile file, boolean typed) {
    int newOffset = editor.getCaretModel().getOffset() - (typed ? 2 : 1);
    if (newOffset < 0) return null;

    PsiElement at = file.findElementAt(newOffset);
    return at != null ? at.getParent() : null;
  }

  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (needToInsertQuotes) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement fileContext = file.getContext();
      String toInsert = tryCompleteQuotes(fileContext);
      boolean showPopup = true;
      boolean showParameterInfo = false;
      if (toInsert == null) {
        final String quote = getDefaultQuote(file);
        AttributeValuePresentation presentation = getValuePresentation(editor, file, quote);
        toInsert = presentation.getPrefix() + presentation.getPostfix();
        showPopup = presentation.showAutoPopup();
        showParameterInfo = "{}".equals(toInsert);
      }
      editor.getDocument().insertString(offset, toInsert);
      editor.getCaretModel().moveToOffset(offset + toInsert.length() / 2);
      if (showPopup) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      }
      if (showParameterInfo) {
        AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
      }
      needToInsertQuotes = false;
      Caret caret = editor.getCaretModel().getCurrentCaret();
      caret.putUserData(QUOTE_INSERTED_AT, toInsert.isEmpty() ? null : new QuoteInfo(toInsert.charAt(0), caret.getOffset()));
    }

    return super.charTyped(c, project, editor, file);
  }

  private static @Nullable String tryCompleteQuotes(@Nullable PsiElement fileContext) {
    if (fileContext != null) {
      if (fileContext.getText().startsWith("\"")) return "''";
      if (fileContext.getText().startsWith("'")) return "\"\"";
    }
    return null;
  }

  private static @NotNull String getDefaultQuote(@NotNull PsiFile file) {
    return XmlEditUtil.getAttributeQuote(file);
  }

  private static @NotNull AttributeValuePresentation getValuePresentation(@NotNull Editor editor, @NotNull PsiFile file, @NotNull String quote) {
    PsiElement atParent = getAttributeCandidate(editor, file, true);
    XmlAttributeDescriptor descriptor;
    if (atParent instanceof XmlAttribute) {
      XmlTag parent = ((XmlAttribute)atParent).getParent();
      return XmlExtension.getExtension(file).getAttributeValuePresentation(parent, ((XmlAttribute)atParent).getName(), quote);
    }
    return XmlExtension.getExtension(file).getAttributeValuePresentation(null, "", quote);
  }

  private record QuoteInfo(char quote, int position) {}

}
