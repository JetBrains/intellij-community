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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.template.zencoding.XmlZenCodingTemplate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XmlSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.XmlSmartEnterProcessor");

  public boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    final PsiElement atCaret = getStatementAtCaret(editor, psiFile);
    XmlTag tagAtCaret = PsiTreeUtil.getParentOfType(atCaret, XmlTag.class);
    if (tagAtCaret != null) {
      try {
        final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tagAtCaret.getNode());
        final ASTNode endTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tagAtCaret.getNode());
        if (emptyTagEnd != null || endTagEnd != null) {
          return XmlZenCodingTemplate.startZenCoding(editor, psiFile);
        }

        int insertionOffset = tagAtCaret.getTextRange().getEndOffset();
        Document doc = editor.getDocument();
        int caretAt = editor.getCaretModel().getOffset();
        final CharSequence text = doc.getCharsSequence();
        final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");
        final PsiElement siebling = tagAtCaret.getNextSibling();
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
            caretAt = valueElement == null ? textRange.getStartOffset() : getClosingQuote(xmlAttribute).length() == 0 ? textRange.getEndOffset() : caretAt;
          }

          if (tagNameText == null) {
            tagNameText = text.subSequence(tagAtCaret.getTextRange().getStartOffset() + 1, caretAt);
          }

          final PsiElement element = psiFile.findElementAt(probableCommaOffset);
          final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
          final CharSequence text2insert = getClosingPart(xmlAttribute, tagAtCaret, false);

          if (tag != null && tag.getTextRange().getStartOffset() == probableCommaOffset) {
            doc.insertString(caretAt, text2insert);
            if (shouldInsertClosingTag(xmlAttribute, tagAtCaret)) {
              doc.insertString(tag.getTextRange().getEndOffset() + text2insert.length(), "</" + tagAtCaret.getName() + ">");
            }

            caretTo = tag.getTextRange().getEndOffset() + text2insert.length();
          }
          else {
            doc.insertString(caretAt, text2insert);
            if (shouldInsertClosingTag(xmlAttribute, tagAtCaret)) {
              doc.insertString(probableCommaOffset + text2insert.length(), "</" + tagNameText + ">");
            }

            caretTo = probableCommaOffset + text2insert.length();
          }
        }
        else if (siebling instanceof XmlTag && siebling.getTextRange().getStartOffset() == caretAt) {
          final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(atCaret, XmlAttribute.class, false, XmlTag.class);
          final CharSequence text2insert = getClosingPart(xmlAttribute, tagAtCaret, false);

          doc.insertString(caretAt, text2insert);
          if (shouldInsertClosingTag(xmlAttribute, tagAtCaret)) {
            doc.insertString(siebling.getTextRange().getEndOffset() + text2insert.length(), "</" + tagAtCaret.getName() + ">");
          }

          caretTo = siebling.getTextRange().getEndOffset() + text2insert.length();
        }
        else if (probableCommaOffset >= text.length() || ((ch = text.charAt(probableCommaOffset)) != '/' && ch != '>')) {
          final XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(atCaret, XmlAttribute.class, false, XmlTag.class);
          final CharSequence text2insert = getClosingPart(xmlAttribute, tagAtCaret, true);

          doc.insertString(insertionOffset, text2insert);
          caretTo = insertionOffset + text2insert.length();
        }

        if (isUncommited(project)) {
          commit(editor);
          tagAtCaret = PsiTreeUtil.getParentOfType(getStatementAtCaret(editor, psiFile), XmlTag.class);
          editor.getCaretModel().moveToOffset(caretTo);
        }

        reformat(tagAtCaret);
        commit(editor);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return true;
  }

  protected boolean shouldInsertClosingTag(final XmlAttribute xmlAttribute, final XmlTag tagAtCaret) {
    return true;
  }

  protected String getClosingPart(final XmlAttribute xmlAttribute, final XmlTag tagAtCaret, final boolean emptyTag) {
    return getClosingQuote(xmlAttribute) + (emptyTag ? "/>" : ">");
  }

  @NotNull
  protected static CharSequence getClosingQuote(@Nullable final XmlAttribute attribute) {
    if (attribute == null) {
      return "";
    }

    final XmlAttributeValue element = attribute.getValueElement();
    if (element == null) {
      return "";
    }

    final String s = element.getText();
    if (s != null && s.length() > 0) {
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
