// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlGtTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result beforeCharTyped(final char c, final @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile editedFile, final @NotNull FileType fileType) {
    final WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    if (c == '>' && webEditorOptions != null && webEditorOptions.isAutomaticallyInsertClosingTag() && fileContainsXmlLanguage(editedFile)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      FileViewProvider provider = editedFile.getViewProvider();
      int offset = editor.getCaretModel().getOffset();

      PsiElement element, elementAtCaret = null;

      if (offset < editor.getDocument().getTextLength()) {
        elementAtCaret = element = provider.findElementAt(offset, XMLLanguage.class);

        if (element == null && offset > 0) {
          // seems like a template language
          // <xml_code><caret><outer_element>
          elementAtCaret = element = provider.findElementAt(offset - 1, XMLLanguage.class);
        }
        if (!(element instanceof PsiWhiteSpace)) {
          boolean nonAcceptableDelimiter = true;

          if (element instanceof XmlToken) {
            IElementType tokenType = ((XmlToken)element).getTokenType();

            if (tokenType == XmlTokenType.XML_START_TAG_START || tokenType == XmlTokenType.XML_END_TAG_START) {
              if (offset > 0) {
                PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);

                if (previousElement instanceof XmlToken) {
                  tokenType = ((XmlToken)previousElement).getTokenType();
                  element = previousElement;
                  nonAcceptableDelimiter = false;
                }
              }
            } else if (tokenType == XmlTokenType.XML_NAME || tokenType == XmlTokenType.XML_TAG_NAME) {
              if (element.getNextSibling() instanceof PsiErrorElement) {
                nonAcceptableDelimiter = false;
              }
            }

            if (tokenType == XmlTokenType.XML_TAG_END ||
                tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END && element.getTextOffset() == offset - 1) {
              EditorModificationUtil.moveCaretRelatively(editor, 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              return Result.STOP;
            }
          }
          if (nonAcceptableDelimiter) return Result.CONTINUE;
        } else {
          // check if right after empty end
          PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);
          if (previousElement instanceof XmlToken) {
            final IElementType tokenType = ((XmlToken)previousElement).getTokenType();

            if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
              return Result.STOP;
            }
            else if (tokenType == XmlTokenType.XML_START_TAG_START) {
              return Result.CONTINUE;
            }
          }
        }

        PsiElement parent = element.getParent();
        if (parent instanceof XmlText) {
          final String text = parent.getText();
          // check /
          final int index = offset - parent.getTextOffset() - 1;

          if (index >= 0 && text.charAt(index)=='/') {
            return Result.CONTINUE; // already seen /
          }
          element = parent.getPrevSibling();
        } else if (parent instanceof XmlTag && !(element.getPrevSibling() instanceof XmlTag) &&
                   !(element.getPrevSibling() instanceof OuterLanguageElement)) {
          element = parent;
        } else if (parent instanceof XmlAttributeValue) {
          element = parent;
        }
      }
      else {
        element = provider.findElementAt(editor.getDocument().getTextLength() - 1, XMLLanguage.class);
        if (element == null) return Result.CONTINUE;
        element = element.getParent();
      }

      if (offset > 0 && offset <= editor.getDocument().getTextLength()) {
        if (editor.getDocument().getCharsSequence().charAt(offset - 1) == '/') { // Some languages (e.g. GSP) allow character '/' in tag name.
          return Result.CONTINUE;
        }
      }

      if (element instanceof XmlAttributeValue) {
        element = element.getParent().getParent();
      }

      while(element instanceof PsiWhiteSpace || element instanceof OuterLanguageElement) element = element.getPrevSibling();
      if (element instanceof XmlDocument) {   // hack for closing tags in ERB
        element = element.getLastChild();
      }
      if (element == null) return Result.CONTINUE;
      if (!(element instanceof XmlTag tag)) {
        if (element instanceof XmlTokenImpl &&
            element.getPrevSibling() !=null &&
            element.getPrevSibling().getText().equals("<")) {
          // tag is started and there is another text in the end
          EditorModificationUtil.insertStringAtCaret(editor, "</" + element.getText() + ">", false, 0);
        }
        return Result.CONTINUE;
      }

      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return Result.CONTINUE;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return Result.CONTINUE;
      final XmlToken startToken = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_START_TAG_START);
      if (startToken == null || !startToken.getText().equals("<")) return Result.CONTINUE;

      String name = tag.getName();
      if (elementAtCaret instanceof XmlToken &&
           (((XmlToken)elementAtCaret).getTokenType() == XmlTokenType.XML_NAME ||
            ((XmlToken)elementAtCaret).getTokenType() == XmlTokenType.XML_TAG_NAME)) {
        name = name.substring(0, offset - elementAtCaret.getTextOffset());
      }
      if (tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag, true)) return Result.CONTINUE;
      if (name.isEmpty()) return Result.CONTINUE;

      int tagOffset = tag.getTextRange().getStartOffset();

      final XmlToken nameToken = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_NAME);
      if (nameToken != null && nameToken.getTextRange().getStartOffset() > offset) return Result.CONTINUE;

      HighlighterIterator iterator = editor.getHighlighter().createIterator(tagOffset);
      if (BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true,true)) {
        PsiElement parent = tag.getParent();
        boolean hasBalance = true;
        loop: while(parent instanceof XmlTag) {
          if (name.equals(((XmlTag)parent).getName())) {
            hasBalance = false;
            ASTNode astNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(parent.getNode());
            if (astNode == null) {
              hasBalance = true;
              break;
            }
            for (PsiElement el = parent.getNextSibling(); el != null; el = el.getNextSibling()) {
              if (el instanceof PsiErrorElement && el.getText().startsWith("</" + name)) {
                hasBalance = true;
                break loop;
              }
            }
          }
          parent = parent.getParent();
        }
        if (hasBalance) return Result.CONTINUE;
      }
      EditorModificationUtil.insertStringAtCaret(editor, "</" + name + ">", false, 0);
      return insertTagContent(project, tag, name, editedFile, editor);
    }
    return Result.CONTINUE;
  }

  protected @NotNull Result insertTagContent(@NotNull Project project,
                                             XmlTag tag,
                                             String name,
                                             PsiFile file, @NotNull Editor editor) {
    return Result.CONTINUE;
  }

  public static boolean fileContainsXmlLanguage(@Nullable PsiFile editedFile) {
    if (editedFile == null) return false;
    if (editedFile.getLanguage() instanceof XMLLanguage) {
      return true;
    }
    if (HtmlUtil.supportsXmlTypedHandlers(editedFile)) {
      return true;
    }
    final FileViewProvider provider = editedFile.getViewProvider();
    if (provider.getBaseLanguage() instanceof XMLLanguage) {
      return true;
    }
    return provider instanceof TemplateLanguageFileViewProvider &&
           ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage() instanceof XMLLanguage;
  }
}