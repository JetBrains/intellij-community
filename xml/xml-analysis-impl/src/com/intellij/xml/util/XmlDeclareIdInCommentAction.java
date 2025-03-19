// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class XmlDeclareIdInCommentAction implements LocalQuickFix {
  private final String myId;

  public XmlDeclareIdInCommentAction(final @NotNull String id) {
    myId = id;
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.declare.id.in.comment");
  }

  public static @Nullable String getImplicitlyDeclaredId(final @NotNull PsiComment comment) {
    final String text = getUncommentedText(comment);
    if (text == null) return null;

    if (text.startsWith("@declare id=\"")) {
      final String result = text.substring("@declare id=\"".length() - 1);
      return StringUtil.unquoteString(result);
    }

    return null;
  }

  private static @Nullable String getUncommentedText(final @NotNull PsiComment comment) {
    final PsiFile psiFile = comment.getContainingFile();
    final Language language = psiFile.getViewProvider().getBaseLanguage();
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    if (commenter != null) {
      String text = comment.getText();

      final String prefix = commenter.getBlockCommentPrefix();
      if (prefix != null && text.startsWith(prefix)) {
        text = text.substring(prefix.length());
        final String suffix = commenter.getBlockCommentSuffix();
        if (suffix != null && text.length() > suffix.length()) {
          return text.substring(0, text.length() - suffix.length()).trim();
        }
      }
    }

    return null;
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile psiFile = psiElement.getContainingFile();

    WriteCommandAction.writeCommandAction(project, psiFile).run(() -> {
      final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
      if (tag == null) return;

      final Language language = psiFile.getViewProvider().getBaseLanguage();
      final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
      if (commenter == null) return;

      String commentText = commenter.getBlockCommentPrefix() + "@declare id=\"" + myId + "\"" + commenter.getBlockCommentSuffix();
      XmlTag parent = tag.getParentTag();
      if (parent != null) {
        Document document = Objects.requireNonNull(psiFile.getViewProvider().getDocument());
        document.insertString(parent.getSubTags()[0].getTextRange().getStartOffset(), commentText);
      }
    });
  }
}
