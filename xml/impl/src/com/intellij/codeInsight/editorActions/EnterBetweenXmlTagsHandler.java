// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

public class EnterBetweenXmlTagsHandler implements EnterHandlerDelegate {
  @Override
  public Result preprocessEnter(final @NotNull PsiFile file, final @NotNull Editor editor, final @NotNull Ref<Integer> caretOffset, final @NotNull Ref<Integer> caretAdvance,
                                final @NotNull DataContext dataContext, final EditorActionHandler originalHandler) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    
    if ((file instanceof XmlFile || HtmlUtil.supportsXmlTypedHandlers(file)) &&
        isBetweenXmlTags(project, editor, file, caretOffset.get().intValue())) {
      editor.getDocument().insertString(caretOffset.get(), "\n");
      if (project != null) {
        CodeStyleManager.getInstance(project).adjustLineIndent(editor.getDocument(), caretOffset.get() + 1);
      }
      return Result.DefaultForceIndent;
    }
    return Result.Continue;
  }

  private static boolean isBetweenXmlTags(Project project, Editor editor, PsiFile file, int offset) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '>') return false;

    EditorHighlighter highlighter = editor.getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (iterator.getTokenType() != XmlTokenType.XML_TAG_END) return false;
    
    if (isAtTheEndOfEmptyTag(project, editor, file, iterator)) {
      return false;
    }
    
    iterator.retreat();

    int retrieveCount = 1;
    while(!iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == XmlTokenType.XML_END_TAG_START) return false;
      if (tokenType == XmlTokenType.XML_START_TAG_START) break;
      ++retrieveCount;
      iterator.retreat();
    }

    for(int i = 0; i < retrieveCount; ++i) iterator.advance();
    iterator.advance();
    return !iterator.atEnd() && iterator.getTokenType() == XmlTokenType.XML_END_TAG_START;
  }

  private static boolean isAtTheEndOfEmptyTag(Project project, Editor editor, PsiFile file, HighlighterIterator iterator) {
    if (iterator.getTokenType() != XmlTokenType.XML_TAG_END) {
      return false;
    }

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final PsiElement element = file.findElementAt(iterator.getStart());

    if (element == null) {
      return false;
    }

    final PsiElement parent = element.getParent();
    return parent instanceof XmlTag &&
           parent.getTextRange().getEndOffset() == iterator.getEnd();
  }
}
