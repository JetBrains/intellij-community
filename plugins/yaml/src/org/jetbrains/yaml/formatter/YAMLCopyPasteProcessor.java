// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLTextUtil;

import java.util.Iterator;
import java.util.List;

public class YAMLCopyPasteProcessor implements CopyPastePreProcessor {
  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (file.getLanguage() != YAMLLanguage.INSTANCE) return text;
    CaretModel caretModel = editor.getCaretModel();
    SelectionModel selectionModel = editor.getSelectionModel();
    Document document = editor.getDocument();
    int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                            selectionModel.getSelectionStart() : caretModel.getOffset();
    int lineNumber = document.getLineNumber(caretOffset);
    int lineStartOffset = YAMLTextUtil.getLineStartSafeOffset(document, lineNumber);
    int indent = caretOffset - lineStartOffset;
    if (indent == 0) {
      // It could be copy and paste of lines
      // User could fix indentation later if he wanted to copy some block into top-level block
      return text;
    }

    return indentText(text, StringUtil.repeatSymbol(' ', indent));
  }

  @NotNull
  private static String indentText(@NotNull String text, @NotNull String curLineIndent) {
    List<String> lines = LineTokenizer.tokenizeIntoList(text, false, false);
    if (lines.isEmpty()) {
      // Such situation should not be possible
      Logger.getInstance(YAMLCopyPasteProcessor.class).error(text.isEmpty()
                                                             ? "Pasted empty text"
                                                             : "Text '" + text + "' was converted into empty line list");
      return text;
    }
    int minIndent = calculateMinBlockIndent(lines);
    String firstLine = lines.iterator().next();
    if (lines.size() == 1) {
      return firstLine;
    }
    return firstLine.substring(YAMLTextUtil.getStartIndentSize(firstLine)) + "\n" +
           lines.stream().skip(1).map(line -> {
             // remove common indent and add needed indent
             if (isEmptyLine(line)) {
               return curLineIndent + line.substring(minIndent);
             }
             else {
               // do not indent empty lines at all
               return "";
             }
           }).reduce((left, right) -> left + "\n" + right).orElse("");
  }

  private static int calculateMinBlockIndent(@NotNull List<String> list) {
    Iterator<String> it = list.iterator();
    String str = "";
    while (it.hasNext()) {
      str = it.next();
      if (isEmptyLine(str)) {
        break;
      }
    }
    if (!it.hasNext()) {
      return 0;
    }
    int minIndent = YAMLTextUtil.getStartIndentSize(str);
    while (it.hasNext()) {
      str = it.next();
      if (isEmptyLine(str)) {
        minIndent = Math.min(minIndent, YAMLTextUtil.getStartIndentSize(str));
      }
    }
    return minIndent;
  }

  private static boolean isEmptyLine(@NotNull String str) {
    return YAMLTextUtil.getStartIndentSize(str) < str.length();
  }
}
