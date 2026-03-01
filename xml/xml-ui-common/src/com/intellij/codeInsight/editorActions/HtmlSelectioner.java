// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HtmlSelectioner extends AbstractWordSelectioner {

  private static final SelectWordUtil.CharCondition JAVA_IDENTIFIER_AND_HYPHEN_CONDITION =
    ch -> Character.isJavaIdentifierPart(ch) || ch == '-';

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return canSelectElement(e);
  }

  static boolean canSelectElement(final PsiElement e) {
    return (e instanceof XmlToken || e instanceof XmlTag)
           && (HtmlUtil.hasHtml(e.getContainingFile()) || HtmlUtil.supportsXmlTypedHandlers(e.getContainingFile()));
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result;

    if (e instanceof XmlTag) {
      result = new ArrayList<>();
      addTagSelection((XmlTag)e, result, editorText);
      return result;
    }
    else if (!(e instanceof XmlToken) ||
             XmlTokenSelectioner.shouldSelectToken((XmlToken)e) ||
             ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      var superResult = super.select(e, editorText, cursorOffset, editor);
      result = superResult != null ? superResult : new ArrayList<>();
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
      if (langFile != psiFile) {
        addAttributeSelection(result, editor, cursorOffset, editorText,
                              fileViewProvider.findElementAt(cursorOffset, lang));
      }
    }
    return result;
  }

  private static void addTagSelection(XmlTag tag, List<? super TextRange> result, @NotNull CharSequence editorText) {
    // A parent can be a JS return statement, so we shouldn't expand the selection to the whole line
    // if the parent does not have a new line inside. Expansion to the whole line should be done on the parent level.
    boolean expandToWholeLine = tag.getParent() != null && tag.getParent().textContains('\n');
    addRangeToResult(result, editorText, expandToWholeLine, tag.getTextRange());

    final ASTNode tagStartEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    final ASTNode tagEndStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tag.getNode());

    if (tagStartEnd != null && tagEndStart != null) {
      var startOffset = tagStartEnd.getTextRange().getEndOffset();
      var endOffset = tagEndStart.getTextRange().getStartOffset();
      if (startOffset < endOffset) {
        addRangeToResult(result, editorText, expandToWholeLine, new TextRange(startOffset, endOffset));
      }
    }
    if (tagStartEnd != null) {
      TextRange range = new TextRange(tag.getTextRange().getStartOffset(),
                                      tagStartEnd.getTextRange().getEndOffset());
      addRangeToResult(result, editorText, expandToWholeLine, range);
    }
    if (tagEndStart != null) {
      TextRange range = new TextRange(tagEndStart.getTextRange().getStartOffset(),
                                      tag.getTextRange().getEndOffset());
      addRangeToResult(result, editorText, expandToWholeLine, range);
    }
  }

  private static void addRangeToResult(List<? super TextRange> result,
                                       @NotNull CharSequence editorText,
                                       boolean expandToWholeLine,
                                       TextRange range) {
    if (expandToWholeLine) {
      result.addAll(expandToWholeLine(editorText, range, false));
    }
    else {
      result.add(range);
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
