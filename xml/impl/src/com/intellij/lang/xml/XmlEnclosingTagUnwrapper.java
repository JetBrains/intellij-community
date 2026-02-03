// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class XmlEnclosingTagUnwrapper implements Unwrapper {
  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return true;
  }

  @Override
  public void collectElementsToIgnore(@NotNull PsiElement element, @NotNull Set<PsiElement> result) {
  }

  @Override
  public @NotNull String getDescription(@NotNull PsiElement e) {
    return XmlBundle.message("xml.action.unwrap.enclosing.tag.name.description", ((XmlTag)e).getName());
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement element, @NotNull List<? super PsiElement> toExtract) {
    final TextRange range = element.getTextRange();
    final ASTNode startTagNameEnd = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
    final ASTNode endTagNameStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(element.getNode());

    int start = startTagNameEnd != null ? startTagNameEnd.getTextRange().getEndOffset() : range.getStartOffset();
    int end = endTagNameStart != null ? endTagNameStart.getTextRange().getStartOffset() : range.getEndOffset();

    for (PsiElement child : element.getChildren()) {
      final TextRange childRange = child.getTextRange();
      if (childRange.getStartOffset() >= start && childRange.getEndOffset() <= end) {
        toExtract.add(child);
      }
    }
    return element;
  }

  @Override
  public @NotNull List<PsiElement> unwrap(@NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    TextRange range = element.getTextRange();
    ASTNode startTagNameEnd = XmlChildRole.START_TAG_END_FINDER.findChild(element.getNode());
    ASTNode endTagNameStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(element.getNode());

    Project project = element.getProject();
    PsiFile file = element.getContainingFile();
    Document document = editor.getDocument();
    RangeMarker marker = document.createRangeMarker(range);
    if (endTagNameStart != null) {
      document.deleteString(endTagNameStart.getTextRange().getStartOffset(), range.getEndOffset());
      document.deleteString(range.getStartOffset(), startTagNameEnd.getTextRange().getEndOffset());
    }
    else {
      document.replaceString(range.getStartOffset(), range.getEndOffset(), "");
    }

    deleteEmptyLine(document, marker.getStartOffset());
    deleteEmptyLine(document, marker.getEndOffset());

    PsiDocumentManager.getInstance(project).commitDocument(document);
    CodeStyleManager.getInstance(project).adjustLineIndent(file, marker.getTextRange());
    return Collections.emptyList();
  }

  protected void deleteEmptyLine(Document document, int offset) {
    int line = offset < document.getTextLength() ? document.getLineNumber(offset) : -1;
    if (line > 0 && DocumentUtil.isLineEmpty(document, line)) {
      int start = document.getLineStartOffset(line);
      int end = Math.min(document.getLineEndOffset(line) + 1, document.getTextLength() - 1);
      if (end == document.getTextLength() - 1) {
        document.deleteString(start - 1, end);
      } else if (start < end) {
        document.deleteString(start, end);
      }
    }
  }
}
