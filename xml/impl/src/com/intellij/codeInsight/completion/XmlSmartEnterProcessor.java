package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class XmlSmartEnterProcessor extends SmartEnterProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.XmlSmartEnterProcessor");

  public void process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile) {
    final PsiElement atCaret = getStatementAtCaret(editor, psiFile);
    XmlTag psiElement = PsiTreeUtil.getParentOfType(atCaret, XmlTag.class);
    if (psiElement != null) {
      try {
        final ASTNode emptyTagEnd = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(psiElement.getNode());
        final ASTNode endTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(psiElement.getNode());
        if (emptyTagEnd != null || endTagEnd != null) return;

        int insertionOffset = psiElement.getTextRange().getEndOffset();
        Document doc = editor.getDocument();
        final int caretAt = editor.getCaretModel().getOffset();
        final CharSequence text = doc.getCharsSequence();
        final int probableCommaOffset = CharArrayUtil.shiftForward(text, insertionOffset, " \t");
        final PsiElement siebling = psiElement.getNextSibling();
        int caretTo = caretAt;
        char ch;

        if (caretAt < probableCommaOffset) {
          final PsiElement element = psiFile.findElementAt(probableCommaOffset);
          final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
          if (tag != null && tag.getTextRange().getStartOffset() == probableCommaOffset) {
            doc.insertString(caretAt, ">");
            doc.insertString(tag.getTextRange().getEndOffset() + 1, "</" + psiElement.getName() + ">");
            caretTo = tag.getTextRange().getEndOffset() + 1;
          }
          else {
            final CharSequence tagName = text.subSequence(psiElement.getTextRange().getStartOffset() + 1, caretAt);
            doc.insertString(caretAt, ">");
            doc.insertString(probableCommaOffset + 1, "</" + tagName + ">");
            caretTo = probableCommaOffset + 1;
          }
        } else if (siebling instanceof XmlTag && siebling.getTextRange().getStartOffset() == caretAt) {
          doc.insertString(caretAt, ">");
          doc.insertString(siebling.getTextRange().getEndOffset() + 1, "</" + psiElement.getName() + ">");
          caretTo = siebling.getTextRange().getEndOffset() + 1;
        } else if (probableCommaOffset >= text.length() || ((ch = text.charAt(probableCommaOffset)) != '/' && ch != '>')) {
          doc.insertString(insertionOffset, "/>");
          caretTo = insertionOffset + 2;
        }

        if (isUncommited(project)) {
          commit(editor);
          psiElement = PsiTreeUtil.getParentOfType(getStatementAtCaret(editor, psiFile), XmlTag.class);
          editor.getCaretModel().moveToOffset(caretTo);
        }

        reformat(psiElement);
        commit(editor);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }


  }
}
