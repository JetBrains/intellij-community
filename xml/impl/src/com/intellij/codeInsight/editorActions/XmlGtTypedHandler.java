package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

public class XmlGtTypedHandler extends TypedHandlerDelegate {
  public boolean beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile editedFile, final FileType fileType) {
    if (c == '>' && editedFile instanceof XmlFile) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      FileViewProvider provider = file.getViewProvider();
      final int offset = editor.getCaretModel().getOffset();

      PsiElement element;

      if (offset < editor.getDocument().getTextLength()) {
        element = provider.findElementAt(offset, XMLLanguage.class);
        if (!(element instanceof PsiWhiteSpace)) {
          boolean nonAcceptableDelimiter = true;

          if (element instanceof XmlToken) {
            IElementType tokenType = ((XmlToken)element).getTokenType();

            if (tokenType == XmlTokenType.XML_START_TAG_START ||
                tokenType == XmlTokenType.XML_END_TAG_START
               ) {
              if (offset > 0) {
                PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);

                if (previousElement instanceof XmlToken) {
                  tokenType = ((XmlToken)previousElement).getTokenType();
                  element = previousElement;
                  nonAcceptableDelimiter = false;
                }
              }
            }

            if (tokenType == XmlTokenType.XML_TAG_END ||
                tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END && element.getTextOffset() == offset - 1
               ) {
              editor.getCaretModel().moveToOffset(offset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              return true;
            }
          }
          if (nonAcceptableDelimiter) return false;
        } else {
          // check if right after empty end
          PsiElement previousElement = provider.findElementAt(offset - 1, XMLLanguage.class);
          if (previousElement instanceof XmlToken) {
            final IElementType tokenType = ((XmlToken)previousElement).getTokenType();

            if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
              return true;
            }
          }
        }

        PsiElement parent = element.getParent();
        if (parent instanceof XmlText) {
          final String text = parent.getText();
          // check /
          final int index = offset - parent.getTextOffset() - 1;

          if (index >= 0 && text.charAt(index)=='/') {
            return false; // already seen /
          }
          element = parent.getPrevSibling();
        } else if (parent instanceof XmlTag && !(element.getPrevSibling() instanceof XmlTag)) {
          element = parent;
        } else if (parent instanceof XmlAttributeValue) {
          element = parent;
        }
      }
      else {
        element = provider.findElementAt(editor.getDocument().getTextLength() - 1, XMLLanguage.class);
        if (element == null) return false;
        element = element.getParent();
      }

      if (element instanceof XmlAttributeValue) {
        element = element.getParent().getParent();
      }

      while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();
      if (element == null) return false;
      if (!(element instanceof XmlTag)) {
        if (element instanceof XmlTokenImpl &&
            element.getPrevSibling() !=null &&
            element.getPrevSibling().getText().equals("<")
           ) {
          // tag is started and there is another text in the end
          editor.getDocument().insertString(offset, "</" + element.getText() + ">");
        }
        return false;
      }

      XmlTag tag = (XmlTag)element;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return false;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return false;
      if (tag instanceof JspXmlTagBase) return false;

      final String name = tag.getName();
      if (tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(name)) return false;
      if ("".equals(name)) return false;

      int tagOffset = tag.getTextRange().getStartOffset();
      HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(tagOffset);
      if (BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true,true)) return false;

      editor.getDocument().insertString(offset, "</" + name + ">");
    }
    return false;
  }
}