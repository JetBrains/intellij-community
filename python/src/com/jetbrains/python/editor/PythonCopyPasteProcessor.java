package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.psi.PyFile;

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

    if (file instanceof PyFile) {
      final int caretOffset = caretModel.getOffset();
      final int lineNumber = document.getLineNumber(caretOffset);
      final int lineStartOffset = getLineStartSafeOffset(document, lineNumber);

      final List<String> strings = StringUtil.split(text, "\n");
      if (StringUtil.countChars(text, '\n') > 0 || StringUtil.startsWithWhitespace(text)) { //2, 3, 4 case from doc
        final PsiElement element = PsiUtilCore.getElementAtOffset(file, caretOffset - 1);

        caretModel.moveToOffset(lineStartOffset);
        String spaceString;
        int indent = 0;

        //calculate indent to normalize text
        if (strings.size() > 0) {
          spaceString = strings.get(0);   // insert single line
          indent = StringUtil.findFirst(spaceString, CharFilter.NOT_WHITESPACE_FILTER);
          if (indent < 0)
            indent = StringUtil.isEmptyOrSpaces(spaceString) ? spaceString.length() : 0;

          if (!StringUtil.startsWithWhitespace(spaceString) && strings.size() > 1) {    // insert multi-line
            spaceString = strings.get(1);
            indent = StringUtil.findFirst(spaceString, CharFilter.NOT_WHITESPACE_FILTER);
            if (indent < 0)
              indent = StringUtil.isEmptyOrSpaces(spaceString) ? spaceString.length() : 0;

            final String trimmed = StringUtil.trimLeading(strings.get(0));    //decrease indent if needed
            if (trimmed.startsWith("def ") || trimmed.startsWith("if ") || trimmed.startsWith("try:") ||
                trimmed.startsWith("class ") || trimmed.startsWith("for ") || trimmed.startsWith("elif ") ||
                trimmed.startsWith("else:") || trimmed.startsWith("except") || trimmed.startsWith("while ")) {
              indent = StringUtil.findFirst(spaceString, CharFilter.NOT_WHITESPACE_FILTER) / 2;
              if (indent < 0) indent = 0;
            }
          }
        }

        if (!StringUtil.isEmptyOrSpaces(text))    // do not process empty lines
          text = StringUtil.trimTrailing(text);

        if (!StringUtil.startsWithWhitespace(text)) {   // add missed whitespaces
          if (indent > 0)
            newText = StringUtil.repeat(" ", indent) + text;
          else
            newText = new String(text);
        }
        else {
          newText = new String(text);   // to indent correctly (see PasteHandler)
        }

        if (element instanceof PsiWhiteSpace &&
            (StringUtil.countChars(element.getText(), '\n') <= 2 && !StringUtil.isEmptyOrSpaces(text))) {
          newText += "\n";
        }
        else
          newText = new String(text);      //user already prepared place to paste to and we just want to indent right
      }
    }
    return newText;
  }

  public static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
  }

}
