package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;

import java.util.List;

/**
 * User : catherine
 */
public class PythonCopyPasteProcessor implements CopyPastePreProcessor {

  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  public String preprocessOnPaste(Project project,
                                  PsiFile file,
                                  Editor editor,
                                  String text,
                                  RawText rawText) {
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE) {
      return text;
    }

    final CaretModel caretModel = editor.getCaretModel();
    final Document document = editor.getDocument();
    String newText = text;

    if (file instanceof PyFile && (StringUtil.startsWithWhitespace(text) || StringUtil.endsWithLineBreak(text) ||
                                   StringUtil.splitByLines(text).length > 1)) {
      if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
      final int caretOffset = caretModel.getOffset();
      final PsiElement element = PsiUtilCore.getElementAtOffset(file, caretOffset-1);
      final int lineNumber = document.getLineNumber(caretOffset);
      final int offset = getLineStartSafeOffset(document, lineNumber);
      final PsiElement element1 = PsiUtilCore.getElementAtOffset(file, offset);
      boolean moved = false;
      if (element instanceof PsiWhiteSpace && element == element1) {
        final PsiElement prevSibling = element.getPrevSibling();
        PyStatementList statementList = null;
        if (prevSibling instanceof PyFunction) {
          statementList = ((PyFunction)prevSibling).getStatementList();
        }
        else if (prevSibling instanceof PyClass) {
          statementList = ((PyClass)prevSibling).getStatementList();
        }
        // Caret beyond actual indent -- move to the actual offset
        if (statementList != null) {
          final PsiElement lastChild = statementList.getLastChild();
          if (lastChild != null) {
            final PsiElement whiteSpace = lastChild.getPrevSibling();
            if (whiteSpace instanceof PsiWhiteSpace) {
              int relatedOffset = whiteSpace.getTextRange().getEndOffset();
              final int indent = relatedOffset - getLineStartSafeOffset(document, document.getLineNumber(relatedOffset));
              if (caretOffset > indent) {
                caretModel.moveToOffset(offset + indent);
                moved = true;
              }
            }
          }
        }

        final List<String> strings = StringUtil.split(element.getText(), "\n");
        //user already prepared place to paste to and we just want to indent right
        if (StringUtil.countChars(element.getText(), '\n') > 2) {
          newText = text + " ";
          if (caretOffset == offset && !strings.isEmpty() && !(element.getParent() instanceof PyFile)) {
            newText = strings.get(strings.size()-1) + newText;
          }
        }
        else {
          newText = text + "\n";
          if (!strings.isEmpty())
            newText += strings.get(strings.size()-1);
          //pasted text'll be the only one statement in block
          if (!element.getText().endsWith("\n") && !moved)
            caretModel.moveToOffset(element.getTextRange().getEndOffset());
        }
      }
    }
    return newText;
  }

  public static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }

}
