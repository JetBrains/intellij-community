// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class HtmlMissingClosingTagInspection extends HtmlLocalInspectionTool {

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!(tag instanceof HtmlTag) || !XmlHighlightVisitor.shouldBeValidated(tag)) {
      return;
    }
    final PsiElement child = tag.getLastChild();
    if (child instanceof PsiErrorElement) {
      return;
    }
    final XmlToken tagNameElement = XmlTagUtil.getStartTagNameElement(tag);
    if (tagNameElement == null) {
      return;
    }
    final String tagName = tagNameElement.getText();
    if (HtmlUtil.isSingleHtmlTag(tag, true) || XmlTagUtil.getEndTagNameElement(tag) != null) {
      return;
    }

    holder.registerProblem(tagNameElement, XmlErrorMessages.message("element.missing.end.tag"),
                           new MissingClosingTagFix(tagName));
  }

  private static class MissingClosingTagFix implements LocalQuickFix {

    private final String myName;

    public MissingClosingTagFix(String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return XmlErrorMessages.message("add.named.closing.tag", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return XmlErrorMessages.message("add.closing.tag");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlToken)) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof XmlTag)) {
        return;
      }
      String text = parent.getText();
      if (text.contains("\n")) {
        int cutoff = -1;
        for (int i = text.length() - 1; i > 0; i--) {
          final char c = text.charAt(i);
          if (c == ' ' || c == '\t') continue;
          if (c == '\n') cutoff = i;
          else break;
        }
        if (cutoff > 0) {
          text = text.substring(0, cutoff);
        }
      }
      final String replacementText = text + "</" + element.getText() + ">";
      final XmlElementFactory factory = XmlElementFactory.getInstance(project);
      final XmlTag newTag = factory.createHTMLTagFromText(replacementText);
      final PsiElement child = parent.getLastChild().copy();
      CodeStyleManager.getInstance(project).performActionWithFormatterDisabled((Runnable)() -> {
        final PsiElement replacement = parent.replace(newTag);
        if (child instanceof XmlText) {
          final PsiElement grandChild = child.getLastChild();
          if (grandChild instanceof PsiWhiteSpace) {
            final XmlTag dummyTag = factory.createHTMLTagFromText("<dummy>" + grandChild.getText() + "</dummy>");
            final XmlText whitespace = PsiTreeUtil.getChildOfType(dummyTag, XmlText.class);
            assert whitespace != null;
            replacement.getParent().addAfter(whitespace, replacement);
          }
        }
      });
    }
  }
}
