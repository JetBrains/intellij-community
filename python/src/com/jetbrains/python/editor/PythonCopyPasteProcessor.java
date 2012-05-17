package com.jetbrains.python.editor;

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
import com.jetbrains.python.psi.PyFile;

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
    final CaretModel caretModel = editor.getCaretModel();
    final Document document = editor.getDocument();

    if (file instanceof PyFile && StringUtil.startsWithWhitespace(text) && StringUtil.endsWithLineBreak(text)) {
      final int caretOffset = caretModel.getOffset();
      final PsiElement element = PsiUtilCore.getElementAtOffset(file, caretOffset-1);
      final int lineNumber = document.getLineNumber(caretOffset);
      final int offset = getLineStartSafeOffset(document, lineNumber);
      final PsiElement element1 = PsiUtilCore.getElementAtOffset(file, offset);
      if (element instanceof PsiWhiteSpace && element == element1) {
        caretModel.moveToOffset(offset);
      }
    }
    return text;
  }

  public int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }

}
