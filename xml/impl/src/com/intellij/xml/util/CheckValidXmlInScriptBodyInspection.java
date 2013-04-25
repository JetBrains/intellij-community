/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 29, 2006
 * Time: 6:09:35 PM
 */
package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
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

/**
 * @author Maxim Mossienko
 */
public class CheckValidXmlInScriptBodyInspection extends XmlSuppressableInspectionTool {
  @NonNls
  private static final String SCRIPT_TAG_NAME = "script";
  private Lexer myXmlLexer;
  @NonNls
  private static final String AMP_ENTITY_REFERENCE = "&amp;";
  @NonNls
  private static final String LT_ENTITY_REFERENCE = "&lt;";

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (SCRIPT_TAG_NAME.equals(tag.getName()) ||
            (tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getName()))
           ) {
          final PsiFile psiFile = tag.getContainingFile();
          final FileType fileType = psiFile.getFileType();

          if (fileType == StdFileTypes.XHTML || fileType == StdFileTypes.JSPX) {
            synchronized(CheckValidXmlInScriptBodyInspection.class) {
              if (myXmlLexer == null) myXmlLexer = new XmlLexer();
              final XmlTagValue tagValue = tag.getValue();
              final String tagBodyText = tagValue.getText();

              if (tagBodyText.length() > 0) {
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
                  if (( tokenType == XmlTokenType.XML_BAD_CHARACTER &&
                        "&".equals(TreeUtil.getTokenText(myXmlLexer))
                      ) ||
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
                      new InsertQuotedCharacterQuickFix(
                        psiFile,
                        psiElement,
                        offsetInElement
                      )
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

  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.valid.script.tag");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckValidXmlInScriptTagBody";
  }

  private static class InsertQuotedCharacterQuickFix implements LocalQuickFix {
    private final PsiFile psiFile;
    private final PsiElement psiElement;
    private final int startInElement;

    public InsertQuotedCharacterQuickFix(PsiFile psiFile, PsiElement psiElement, int startInElement) {
      this.psiFile = psiFile;
      this.psiElement = psiElement;
      this.startInElement = startInElement;
    }

    @NotNull
    public String getName() {
      final String character = getXmlCharacter();

      return XmlBundle.message(
        "unescaped.xml.character.fix.message",
        character.equals("&") ?
          XmlBundle.message("unescaped.xml.character.fix.message.parameter"):
          character
      );
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
      if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
      final TextRange range = psiElement.getTextRange();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(
        project,
        psiFile.getVirtualFile(),
        range.getStartOffset() + startInElement
      );

      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      if (editor == null) return;

      final String xmlCharacter = getXmlCharacter();
      String replacement = xmlCharacter.equals("&") ? AMP_ENTITY_REFERENCE : LT_ENTITY_REFERENCE;
      replacement = psiElement.getText().replace(xmlCharacter,replacement);

      editor.getDocument().replaceString(
        range.getStartOffset(),
        range.getEndOffset(),
        replacement
      );
    }

    private String getXmlCharacter() {
      return psiElement.getText().substring(startInElement, startInElement + 1);
    }
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
