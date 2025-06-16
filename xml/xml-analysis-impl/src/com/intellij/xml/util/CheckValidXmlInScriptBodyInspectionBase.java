// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CheckValidXmlInScriptBodyInspectionBase extends XmlSuppressableInspectionTool {
  private Lexer myXmlLexer;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final @NotNull XmlTag tag) {
        if (HtmlUtil.isHtmlTag(tag)) return;
        
        if (HtmlUtil.SCRIPT_TAG_NAME.equals(tag.getName()) ||
            tag instanceof HtmlTag && HtmlUtil.SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getName())) {
          final PsiFile psiFile = tag.getContainingFile();
          final FileType fileType = psiFile.getFileType();

          if (fileType instanceof XmlLikeFileType) {
            synchronized(CheckValidXmlInScriptBodyInspectionBase.class) {
              if (myXmlLexer == null) myXmlLexer = new XmlLexer();
              final XmlTagValue tagValue = tag.getValue();
              final String tagBodyText = tagValue.getText();

              if (!tagBodyText.isEmpty()) {
                myXmlLexer.start(tagBodyText);

                while(myXmlLexer.getTokenType() != null) {
                  IElementType tokenType = myXmlLexer.getTokenType();

                  if (tokenType == XmlTokenType.XML_CDATA_START) {
                    while(tokenType != null && tokenType != XmlTokenType.XML_CDATA_END) {
                      myXmlLexer.advance();
                      tokenType = myXmlLexer.getTokenType();
                    }
                    if (tokenType == null) break;
                  }
                  if (tokenType == XmlTokenType.XML_BAD_CHARACTER &&
                        "&".equals(TreeUtil.getTokenText(myXmlLexer)) ||
                      tokenType == XmlTokenType.XML_START_TAG_START
                    ) {
                    final int valueStart = tagValue.getTextRange().getStartOffset();
                    final int offset = valueStart + myXmlLexer.getTokenStart();
                    final PsiElement psiElement = psiFile.findElementAt(offset);
                    final TextRange elementRange = psiElement.getTextRange();

                    final int offsetInElement = offset - elementRange.getStartOffset();
                    holder.registerProblem(
                      psiElement,
                      XmlAnalysisBundle.message("xml.inspections.unescaped.xml.character"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      createFix(psiElement, offsetInElement)
                    );

                    int endOfElementInScriptTag = elementRange.getEndOffset() - valueStart;
                    while(myXmlLexer.getTokenEnd() < endOfElementInScriptTag) {
                      myXmlLexer.advance();
                      if (myXmlLexer.getTokenType() == null) break;
                    }
                  }
                  myXmlLexer.advance();
                }
              }
            }
          }
        }
      }
    };
  }

  protected LocalQuickFix createFix(PsiElement psiElement, int offsetInElement) {
    return null;
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return "CheckValidXmlInScriptTagBody";
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
