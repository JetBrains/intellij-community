/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
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
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CheckValidXmlInScriptBodyInspectionBase extends XmlSuppressableInspectionTool {
  @NonNls
  protected static final String AMP_ENTITY_REFERENCE = "&amp;";
  @NonNls
  protected static final String LT_ENTITY_REFERENCE = "&lt;";
  @NonNls
  private static final String SCRIPT_TAG_NAME = "script";
  private Lexer myXmlLexer;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (HtmlUtil.isHtmlTag(tag)) return;
        
        if (SCRIPT_TAG_NAME.equals(tag.getName()) ||
            tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getName())) {
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
                      XmlBundle.message("unescaped.xml.character"),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      createFix(psiFile, psiElement, offsetInElement)
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

  protected LocalQuickFix createFix(PsiFile psiFile, PsiElement psiElement, int offsetInElement) {
    return null;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.valid.script.tag");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckValidXmlInScriptTagBody";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
