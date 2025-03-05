// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.CheckEmptyTagInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance(XmlSmartEnterProcessor.class);

  @Override
  public boolean process(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    return completeEndTag(project, editor, psiFile);
  }

  private boolean completeEndTag(Project project, Editor editor, PsiFile psiFile) {
    final PsiElement atCaret = getStatementAtCaret(editor, psiFile);
    XmlTag tagAtCaret = PsiTreeUtil.getParentOfType(atCaret, XmlTag.class);
    if (tagAtCaret == null) {
      return false;
    }
    try {
      final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tagAtCaret.getNode());
      final ASTNode endTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tagAtCaret.getNode());
      final Document doc = editor.getDocument();
      if (emptyTagEnd == null && endTagEnd == null) {
        int insertionOffset = tagAtCaret.getTextRange().getEndOffset();
        int caretAt = editor.getCaretModel().getOffset();
        final CharSequence text = doc.getCharsSequence();
        final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");
        final PsiElement sibling = tagAtCaret.getNextSibling();
        int caretTo = caretAt;
        char ch;

        if (caretAt < probableCommaOffset) {
          final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(atCaret, XmlAttribute.class, false, XmlTag.class);

          CharSequence tagNameText = null;
          if (xmlAttribute != null) {
            final ASTNode node = tagAtCaret.getNode();
            if (node != null) {
              final ASTNode tagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
              if (tagName != null) {
                tagNameText = tagName.getText();
              }
            }

            final XmlAttributeValue valueElement = xmlAttribute.getValueElement();
            final TextRange textRange = xmlAttribute.getTextRange();
            caretAt = valueElement == null
                      ? textRange.getStartOffset()
                      : getClosingQuote(xmlAttribute).isEmpty() ? textRange.getEndOffset() : caretAt;
          }

          if (tagNameText == null) {
            int start = tagAtCaret.getTextRange().getStartOffset() + 1;
            if (start > caretAt) {
              return false;
            }
            tagNameText = text.subSequence(start, caretAt);
          }

          final PsiElement element = psiFile.findElementAt(probableCommaOffset);
          final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
          boolean shouldInsertClosingTag = shouldAfterWrapTextWithTag(caretAt, probableCommaOffset) || shouldInsertClosingTag(xmlAttribute, tagAtCaret);
          final CharSequence text2insert = getClosingPart(xmlAttribute, tagAtCaret, !shouldInsertClosingTag);

          if (tag != null && tag.getTextRange().getStartOffset() == probableCommaOffset) {
            doc.insertString(caretAt, text2insert);
            if (shouldInsertClosingTag) {
              doc.insertString(tag.getTextRange().getEndOffset() + text2insert.length(), "</" + tagAtCaret.getName() + ">");
            }

            caretTo = tag.getTextRange().getEndOffset() + text2insert.length();
          }
          else {
            doc.insertString(caretAt, text2insert);
            if (shouldInsertClosingTag) {
              doc.insertString(probableCommaOffset + text2insert.length(), "</" + tagNameText + ">");
            }

            caretTo = probableCommaOffset + text2insert.length();
          }
        }
        else if (sibling instanceof XmlTag && sibling.getTextRange().getStartOffset() == caretAt) {
          final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(atCaret, XmlAttribute.class, false, XmlTag.class);
          final CharSequence text2insert = getClosingPart(xmlAttribute, tagAtCaret, false);

          doc.insertString(caretAt, text2insert);
          if (shouldInsertClosingTag(xmlAttribute, tagAtCaret)) {
            doc.insertString(sibling.getTextRange().getEndOffset() + text2insert.length(), "</" + tagAtCaret.getName() + ">");
          }

          caretTo = sibling.getTextRange().getEndOffset() + text2insert.length();
        }
        else if (probableCommaOffset >= text.length() || ((ch = text.charAt(probableCommaOffset)) != '/' && ch != '>')) {
          final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(atCaret, XmlAttribute.class, false, XmlTag.class);
          final String text2insert = getClosingPart(xmlAttribute, tagAtCaret, true);

          doc.insertString(insertionOffset, text2insert);
          caretTo = insertionOffset + text2insert.indexOf('>') + 1;
        }

        commitChanges(project, editor, psiFile, caretTo, null);
      }
      else {
        final XmlTag unclosedTag = findClosestUnclosedTag(tagAtCaret);
        if (unclosedTag == null) {
          return false;
        }

        final String closingTagString = "</" + unclosedTag.getName() + ">";

        final XmlTag parentTag = unclosedTag.getParentTag();
        final ASTNode parentEndTagNode = parentTag != null ? XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parentTag.getNode()) : null;
        final int offset = parentEndTagNode != null
                           ? parentEndTagNode.getTextRange().getStartOffset()
                           : unclosedTag.getTextRange().getEndOffset();

        doc.insertString(offset, closingTagString);
        commitChanges(project, editor, psiFile, offset, parentTag != null ? parentTag : unclosedTag);
      }
      return true;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return false;
  }

  protected boolean shouldAfterWrapTextWithTag(int caretAt, int probableCommaOffset) {
    return probableCommaOffset > caretAt;
  }

  private void commitChanges(Project project, Editor editor, PsiFile psiFile, int caretOffset, @Nullable XmlTag tagToReformat) {
    if (isUncommited(project)) {
      commit(editor);
      if (tagToReformat == null) {
        tagToReformat = PsiTreeUtil.getParentOfType(getStatementAtCaret(editor, psiFile), XmlTag.class);
      }
      editor.getCaretModel().moveToOffset(caretOffset);
    }
    if (tagToReformat != null) {
      reformat(tagToReformat);
    }
    commit(editor);
  }

  private static @Nullable XmlTag findClosestUnclosedTag(final XmlTag tag) {
    XmlTag unclosedTag = tag;
    while (unclosedTag != null) {
      final PsiElement lastChild = unclosedTag.getLastChild();
      if (isTagUnclosed(lastChild)) {
        return unclosedTag;
      }
      final XmlTag prevTag = PsiTreeUtil.getPrevSiblingOfType(unclosedTag, XmlTag.class);
      unclosedTag = prevTag != null ? prevTag : PsiTreeUtil.getParentOfType(unclosedTag, XmlTag.class);
    }
    return null;
  }

  protected static boolean isTagUnclosed(PsiElement lastChild) {
  //strange approach, but it's universal for xml and html
    return lastChild != null &&
           lastChild.getNode().getElementType() != XmlTokenType.XML_TAG_END &&
           lastChild.getNode().getElementType() != XmlTokenType.XML_EMPTY_ELEMENT_END;
  }

  protected boolean shouldInsertClosingTag(final XmlAttribute xmlAttribute, final XmlTag tagAtCaret) {
    return xmlAttribute == null || !getClosingQuote(xmlAttribute).isEmpty();
  }

  protected String getClosingPart(final XmlAttribute xmlAttribute, final XmlTag tagAtCaret, final boolean emptyTag) {
    return getClosingQuote(xmlAttribute) +
           (emptyTag ?
              CheckEmptyTagInspection.isTagWithEmptyEndNotAllowed(tagAtCaret) ? "></" + tagAtCaret.getName() + ">"  : "/>" :
              ">");
  }

  protected static @NotNull CharSequence getClosingQuote(final @Nullable XmlAttribute attribute) {
    if (attribute == null) {
      return "";
    }

    final XmlAttributeValue element = attribute.getValueElement();
    if (element == null) {
      return "";
    }

    final String s = element.getText();
    if (s != null && !s.isEmpty()) {
      if (s.charAt(0) == '"' && s.charAt(s.length() - 1) != '"') {
        return "\"";
      }
      else if (s.charAt(0) == '\'' && s.charAt(s.length() - 1) != '\'') {
        return "'";
      }
    }

    return "";
  }
}
