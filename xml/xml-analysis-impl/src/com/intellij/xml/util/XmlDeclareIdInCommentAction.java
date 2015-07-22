/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlDeclareIdInCommentAction implements LocalQuickFix {
  private final String myId;

  public XmlDeclareIdInCommentAction(@NotNull final String id) {
    myId = id;
  }

  @Override
  @NotNull
  public String getName() {
    return XmlErrorMessages.message("declare.id.in.comment.quickfix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Nullable
  public static String getImplicitlyDeclaredId(@NotNull final PsiComment comment) {
    final String text = getUncommentedText(comment);
    if (text == null) return null;

    if (text.startsWith("@declare id=\"")) {
      final String result = text.substring("@declare id=\"".length() - 1);
      return StringUtil.unquoteString(result);
    }

    return null;
  }

  @Nullable
  private static String getUncommentedText(@NotNull final PsiComment comment) {
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
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile psiFile = psiElement.getContainingFile();

    new WriteCommandAction(project, psiFile) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
        if (tag == null) return;

        final Language language = psiFile.getViewProvider().getBaseLanguage();
        final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
        if (commenter == null) return;

        final PsiFile tempFile = PsiFileFactory.getInstance(project).createFileFromText("dummy", language.getAssociatedFileType(),
            commenter.getBlockCommentPrefix() +
                "@declare id=\"" +
                myId +
                "\"" +
                commenter.getBlockCommentSuffix() +
                "\n");

        final XmlTag parent = tag.getParentTag();
        if (parent != null && parent.isValid()) {
          final XmlTag[] tags = parent.getSubTags();
          if (tags.length > 0) {
            final PsiFile psi = tempFile.getViewProvider().getPsi(language);
            if (psi != null) {
              final PsiElement element = psi.findElementAt(1);
              if (element instanceof PsiComment) {
                parent.getNode().addChild(element.getNode(), tags[0].getNode());
              }
            }
          }
        }
      }
    }.execute();
  }
}
