// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

final class XmlSplitTagAction implements IntentionAction {
  @Override
  public @NotNull String getText() {
    return XmlBundle.message("xml.intention.split.tag.text");
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlBundle.message("xml.intention.split.tag.family");
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      if (editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement psiElement = psiFile.findElementAt(offset);
        if (psiElement != null) {
          final PsiElement parent = psiElement.getParent();
          if (parent instanceof XmlText && !parent.getText().trim().isEmpty()) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent != null && !isInsideUnsplittableElement(grandParent)) {
              return InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, offset) == null;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isInsideUnsplittableElement(final PsiElement grandParent) {
    if (!(grandParent instanceof HtmlTag) && grandParent.getContainingFile().getLanguage() != XHTMLLanguage.INSTANCE) {
      return false;
    }

    final String name = ((XmlTag)grandParent).getName();
    return "html".equals(name) || "body".equals(name) || "title".equals(name);
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    if (editor != null) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement psiElement = psiFile.findElementAt(offset);
      if (psiElement != null) {
        final PsiElement containingTag = psiElement.getParent().getParent();
        if (containingTag instanceof XmlTag tag) {
          TextRange tagRange = tag.getTextRange();

          String name = tag.getName();
          String toInsert = "</" + name + "><" + name + getAttrsWithoutId(tag) + ">";
          editor.getDocument().insertString(offset, toInsert);
          editor.getCaretModel().moveToOffset(offset + toInsert.length());
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

          CodeStyleManager.getInstance(project).reformatRange(psiFile, tagRange.getStartOffset(), tagRange.getEndOffset() + toInsert.length());
        }
      }
    }
  }

  private static String getAttrsWithoutId(XmlTag xmlTag) {
    final StringBuilder attrsWoId = new StringBuilder();
    for (XmlAttribute attribute : xmlTag.getAttributes()) {
      if (!HtmlUtil.ID_ATTRIBUTE_NAME.equals(attribute.getName())) {
        attrsWoId.append(attribute.getText()).append(" ");
      }
    }
    return attrsWoId.isEmpty() ? "" : " " + attrsWoId;
  } 

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
