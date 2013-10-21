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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 10:30:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class HtmlSelectioner extends AbstractWordSelectioner {

  private static final SelectWordUtil.CharCondition JAVA_IDENTIFIER_AND_HYPHEN_CONDITION = new SelectWordUtil.CharCondition() {
    @Override
    public boolean value(char ch) {
      return Character.isJavaIdentifierPart(ch) || ch == '-';
    }
  };
  private static final String CLASS_ATTRIBUTE_NAME = "class";

  public boolean canSelect(PsiElement e) {
    return canSelectElement(e);
  }

  static boolean canSelectElement(final PsiElement e) {
    if (e instanceof XmlToken) {
      return HtmlUtil.hasHtml(e.getContainingFile());
    }
    return false;
  }

  public List<TextRange> select(PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result;

    if (!(e instanceof XmlToken) ||
        XmlTokenSelectioner.shouldSelectToken((XmlToken)e) ||
        ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      result = super.select(e, editorText, cursorOffset, editor);
    }
    else {
      result = ContainerUtil.newArrayList();
    }

    final PsiElement parent = e.getParent();
    if (parent instanceof XmlComment) {
      result.addAll(expandToWholeLine(editorText, parent.getTextRange(), true));
    }

    PsiFile psiFile = e.getContainingFile();
    FileType fileType = psiFile.getVirtualFile().getFileType();

    addAttributeSelection(result, editor, editorText, e);
    final FileViewProvider fileViewProvider = psiFile.getViewProvider();
    for (Language lang : fileViewProvider.getLanguages()) {
      final PsiFile langFile = fileViewProvider.getPsi(lang);
      if (langFile != psiFile) addAttributeSelection(result, editor, editorText, fileViewProvider.findElementAt(cursorOffset, lang));
    }

    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(e.getProject(), psiFile.getVirtualFile());
    highlighter.setText(editorText);

    addTagSelection(editorText, cursorOffset, fileType, highlighter, result);

    return result;
  }

  private static void addTagSelection(CharSequence editorText, int cursorOffset, FileType fileType,
                                      @NotNull EditorHighlighter highlighter, @NotNull List<TextRange> result) {
    int start = cursorOffset;

    while (true) {
      if (start < 0) return;
      HighlighterIterator i = highlighter.createIterator(start);
      if (i.atEnd()) return;

      while (true) {
        if (i.getTokenType() == XmlTokenType.XML_START_TAG_START) break;
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
          result.add(new UnfairTextRange(j.getEnd(), i.getStart()));
        }
        if (!j.atEnd()) {
          result.add(new TextRange(start, j.getEnd()));
        }
        if (!i.atEnd()) {
          result.add(new TextRange(i.getStart(), tagEnd));
        }
      }

      start--;
    }
  }

  private static void addAttributeSelection(@NotNull List<TextRange> result, @NotNull Editor editor, 
                                            @NotNull CharSequence editorText, @Nullable PsiElement e) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(e, XmlAttribute.class);

    if (attribute != null) {
      result.add(attribute.getTextRange());
      final XmlAttributeValue value = attribute.getValueElement();

      if (value != null) {
        if (CLASS_ATTRIBUTE_NAME.equalsIgnoreCase(attribute.getName())) {
          addClassAttributeRanges(result, editor, editorText, value);
        }
        final TextRange range = value.getTextRange();
        result.add(range);
        if (value.getFirstChild() != null &&
            value.getFirstChild().getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
          result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));
        }
      }
    }
  }

  @Override
  public int getMinimalTextRangeLength(@NotNull PsiElement element, @NotNull CharSequence text, int cursorOffset) {
    if (WebEditorOptions.getInstance().isSelectWholeCssSelectorSuffixOnDoubleClick()) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
      final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
      if (attribute != null && attributeValue != null) {
        if (CLASS_ATTRIBUTE_NAME.equalsIgnoreCase(attribute.getName())) {
          final TextRange valueTextRange = attributeValue.getValueTextRange();
          if (!valueTextRange.isEmpty()) {
            int start = cursorOffset;
            int end = cursorOffset;
            while (start > valueTextRange.getStartOffset()) {
              if (!JAVA_IDENTIFIER_AND_HYPHEN_CONDITION.value(text.charAt(start - 1))) {
                break;
              }
              start--;
            }

            while (end < valueTextRange.getEndOffset()) {
              if (!JAVA_IDENTIFIER_AND_HYPHEN_CONDITION.value(text.charAt(end + 1))) {
                break;
              }
              end++;
            }
            return end - start;
          }
        }
      }
    }
    return super.getMinimalTextRangeLength(element, text, cursorOffset);
  }

  private static void addClassAttributeRanges(@NotNull List<TextRange> result, @NotNull Editor editor, 
                                              @NotNull CharSequence editorText, @NotNull XmlAttributeValue attributeValue) {
    final TextRange attributeValueTextRange = attributeValue.getTextRange();
    final LinkedList<TextRange> wordRanges = ContainerUtil.newLinkedList();
    SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, editor.getCaretModel().getOffset(), wordRanges,
                                    JAVA_IDENTIFIER_AND_HYPHEN_CONDITION);
    for (TextRange range : wordRanges) {
      if (attributeValueTextRange.contains(range)) {
        result.add(range);
      }
    }
  }
}
