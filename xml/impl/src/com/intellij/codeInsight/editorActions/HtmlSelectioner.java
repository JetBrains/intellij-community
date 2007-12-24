/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 10:30:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;

import java.util.List;

public class HtmlSelectioner extends AbstractWordSelectioner {
  private static ExtendWordSelectionHandler ourStyleSelectioner;

  public static void setStyleSelectioner(ExtendWordSelectionHandler _styleSelectioner) {
    ourStyleSelectioner = _styleSelectioner;
  }

  public boolean canSelect(PsiElement e) {
    return canSelectElement(e);
  }

  static boolean canSelectElement(final PsiElement e) {
    if (e instanceof XmlToken) {
      Language language = e.getLanguage();
      if (language == StdLanguages.XML) language = e.getParent().getLanguage();

      return language instanceof HTMLLanguage ||
             language instanceof XHTMLLanguage ||
             language == StdLanguages.JSP ||
             language == StdLanguages.JSPX ||
             (ourStyleSelectioner != null && ourStyleSelectioner.canSelect(e));
    }
    return false;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (ourStyleSelectioner!=null) {
      List<TextRange> o = ourStyleSelectioner.select(e, editorText, cursorOffset, editor);
      if (o!=null) result.addAll(o);
    }

    final PsiElement parent = e.getParent();
    if (parent instanceof XmlComment) {
      result.addAll(expandToWholeLine(editorText, parent.getTextRange(), true));
    }
    
    PsiFile psiFile = e.getContainingFile();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(psiFile.getVirtualFile());

    addAttributeSelection(result, e);
    final FileViewProvider fileViewProvider = psiFile.getViewProvider();
    for(Language lang:fileViewProvider.getPrimaryLanguages()) {
      final PsiFile langFile = fileViewProvider.getPsi(lang);
      if (langFile != psiFile) addAttributeSelection(result, fileViewProvider.findElementAt(cursorOffset, lang));
    }

    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(e.getProject(), psiFile.getVirtualFile());
    highlighter.setText(editorText);

    addTagSelection(editorText, cursorOffset, fileType, highlighter, result);

    return result;
  }

  private static void addTagSelection(CharSequence editorText, int cursorOffset, FileType fileType, EditorHighlighter highlighter, List<TextRange> result) {
    int start = cursorOffset;

    while (true) {
      if (start < 0) return;
      HighlighterIterator i = highlighter.createIterator(start);
      if (i.atEnd()) return;

      while (true) {
        if (i.getTokenType() ==  XmlTokenType.XML_START_TAG_START) break;
        i.retreat();
        if (i.atEnd()) return;
      }

      start = i.getStart();
      final boolean matched = BraceMatchingUtil.matchBrace(editorText, fileType, i, true);

      if (matched) {
        final int tagEnd = i.getEnd();
        result.add(new TextRange(start, tagEnd));

        HighlighterIterator j = highlighter.createIterator(start);
        while (!j.atEnd() && j.getTokenType() != XmlTokenType.XML_TAG_END) j.advance();
        while (!i.atEnd() && i.getTokenType() != XmlTokenType.XML_END_TAG_START) i.retreat();

        if (!i.atEnd() && !j.atEnd()) {
          result.add(new TextRange(j.getEnd(), i.getStart()));
        }
        if (!j.atEnd()) {
          result.add(new TextRange(start, j.getEnd()));
        }
        if (!i.atEnd()) {
          result.add(new TextRange(i.getStart(),tagEnd));
        }
      }

      start--;
    }
  }

  private static void addAttributeSelection(List<TextRange> result, PsiElement e) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(e, XmlAttribute.class);
    
    if (attribute != null) {
      result.add(attribute.getTextRange());
      final XmlAttributeValue value = attribute.getValueElement();

      if (value != null) {
        final TextRange range = value.getTextRange();
        result.add(range);
        if (value.getFirstChild() != null && value.getFirstChild().getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
          result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));
        }
      }
    }
  }
}
