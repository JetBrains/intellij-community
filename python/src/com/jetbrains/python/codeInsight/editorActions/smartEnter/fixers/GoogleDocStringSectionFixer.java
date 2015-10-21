/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.GoogleCodeStyleDocStringBuilder;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class GoogleDocStringSectionFixer extends PyFixer<PyStringLiteralExpression> {
  public GoogleDocStringSectionFixer() {
    super(PyStringLiteralExpression.class);
  }

  @Override
  protected boolean isApplicable(@NotNull Editor editor, @NotNull PyStringLiteralExpression pyString) {
    return DocStringUtil.getParentDefinitionDocString(pyString) == pyString &&
           DocStringUtil.guessDocStringFormat(pyString.getText(), pyString) == DocStringFormat.GOOGLE;
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyStringLiteralExpression pyString) {
    final int offset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    final int lineNum = document.getLineNumber(offset);
    final int lineStart = document.getLineStartOffset(lineNum);
    final int lineEnd = document.getLineEndOffset(lineNum);
    final String line = document.getText(TextRange.create(lineStart, lineEnd));
    if (!StringUtil.isEmptyOrSpaces(line)) {
      final String trimmedLine = line.trim();
      final String header = trimmedLine.endsWith(":") ? trimmedLine.substring(0, trimmedLine.length() - 1) : trimmedLine;
      if (SectionBasedDocString.isValidSectionTitle(header)) {
        final String patch = (trimmedLine.endsWith(":") ? "\n" : ":\n") +
                             PyIndentUtil.getLineIndent(line) +
                             GoogleCodeStyleDocStringBuilder.getDefaultSectionIndent(pyString.getProject());
        final int insertionOffset = lineStart + StringUtil.trimTrailing(line).length();
        document.replaceString(insertionOffset, lineEnd, patch);
        processor.registerUnresolvedError(insertionOffset + patch.length());
      }
    }
  }
}
