// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HtmlSelectioner extends AbstractWordSelectioner {

  private static final SelectWordUtil.CharCondition JAVA_IDENTIFIER_AND_HYPHEN_CONDITION = ch -> Character.isJavaIdentifierPart(ch) || ch == '-';

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return canSelectElement(e);
  }

  static boolean canSelectElement(final PsiElement e) {
    if (e instanceof XmlToken) {
      return HtmlUtil.hasHtml(e.getContainingFile()) || HtmlUtil.supportsXmlTypedHandlers(e.getContainingFile());
    }
    return false;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result;

    if (!(e instanceof XmlToken) ||
        XmlTokenSelectioner.shouldSelectToken((XmlToken)e) ||
        ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      result = super.select(e, editorText, cursorOffset, editor);
    }
    else {
      result = new ArrayList<>();
    }

    final PsiElement parent = e.getParent();
    if (parent instanceof XmlComment) {
      result.addAll(expandToWholeLine(editorText, parent.getTextRange(), true));
    }

    PsiFile psiFile = e.getContainingFile();

    addAttributeSelection(result, editor, cursorOffset, editorText, e);
    final FileViewProvider fileViewProvider = psiFile.getViewProvider();
    for (Language lang : fileViewProvider.getLanguages()) {
      final PsiFile langFile = fileViewProvider.getPsi(lang);
      if (langFile != psiFile) addAttributeSelection(result, editor, cursorOffset, editorText,
                                                     fileViewProvider.findElementAt(cursorOffset, lang));
    }

    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(e.getProject(), psiFile.getVirtualFile());
    highlighter.setText(editorText);

    addTagSelection2(e, result);

    return result;
  }

  private static void addTagSelection2(PsiElement e, List<? super TextRange> result) {
    XmlTag tag = PsiTreeUtil.getParentOfType(e, XmlTag.class, true);
    while (tag != null) {
      result.add(tag.getTextRange());
      final ASTNode tagStartEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
      final ASTNode tagEndStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tag.getNode());
      if (tagStartEnd != null && tagEndStart != null) {
        result.add(new UnfairTextRange(tagStartEnd.getTextRange().getEndOffset(),
                                       tagEndStart.getTextRange().getStartOffset()));
      }
      if (tagStartEnd != null) {
        result.add(new TextRange(tag.getTextRange().getStartOffset(),
                                 tagStartEnd.getTextRange().getEndOffset()));
      }
      if (tagEndStart != null) {
        result.add(new TextRange(tagEndStart.getTextRange().getStartOffset(),
                                 tag.getTextRange().getEndOffset()));
      }
      tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class, true);
    }
  }

  private void addAttributeSelection(@NotNull List<? super TextRange> result, @NotNull Editor editor, int cursorOffset,
                                     @NotNull CharSequence editorText, @Nullable PsiElement e) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(e, XmlAttribute.class);

    if (attribute != null) {
      result.add(attribute.getTextRange());
      final XmlAttributeValue value = attribute.getValueElement();

      if (value != null) {
        if (getClassAttributeName().equalsIgnoreCase(attribute.getName())) {
          addClassAttributeRanges(result, editor, cursorOffset, editorText, value);
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
    if (WebEditorOptions.getInstance().isSelectWholeCssIdentifierOnDoubleClick()) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
      final XmlAttributeValue attributeValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
      if (attribute != null && attributeValue != null) {
        if (getClassAttributeName().equalsIgnoreCase(attribute.getName())) {
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

  protected @NotNull String getClassAttributeName() {
    return HtmlUtil.CLASS_ATTRIBUTE_NAME;
  }

  public static void addClassAttributeRanges(@NotNull List<? super TextRange> result, @NotNull Editor editor, int cursorOffset,
                                             @NotNull CharSequence editorText, @NotNull XmlAttributeValue attributeValue) {
    final TextRange attributeValueTextRange = attributeValue.getTextRange();
    final LinkedList<TextRange> wordRanges = new LinkedList<>();
    SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, wordRanges,
                                    JAVA_IDENTIFIER_AND_HYPHEN_CONDITION);
    for (TextRange range : wordRanges) {
      if (attributeValueTextRange.contains(range)) {
        result.add(range);
      }
    }
  }
}
