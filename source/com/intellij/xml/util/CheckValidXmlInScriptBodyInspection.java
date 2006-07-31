/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 29, 2006
 * Time: 6:09:35 PM
 */
package com.intellij.xml.util;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.GroupNames;
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
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckValidXmlInScriptBodyInspection extends LocalInspectionTool {
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

  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      public void visitXmlTag(final XmlTag tag) {
        if (SCRIPT_TAG_NAME.equals(tag.getName()) ||
            (tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getLocalName()))
           ) {
          final PsiFile psiFile = tag.getContainingFile();
          final FileType fileType = psiFile.getFileType();

          if (fileType == StdFileTypes.XHTML || fileType == StdFileTypes.JSPX) {
            synchronized(CheckValidXmlInScriptBodyInspection.class) {
              if (myXmlLexer == null) myXmlLexer = new XmlLexer();
              final XmlTagValue tagValue = tag.getValue();
              final String tagBodyText = tagValue.getText();

              if (tagBodyText.length() > 0) {
                myXmlLexer.start(tagBodyText.toCharArray());

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
                        "&".equals(ParseUtil.getTokenText(myXmlLexer))
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

  public String getGroupDisplayName() {
    return GroupNames.HTML_INSPECTIONS;
  }

  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.valid.script.tag");
  }

  @NonNls
  public String getShortName() {
    return "CheckValidXmlInScriptBody";
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

    public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
      if ( !CodeInsightUtil.prepareFileForWrite(psiFile)) return;
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

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}