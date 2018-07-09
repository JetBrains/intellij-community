/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyStringLiteralFixer extends PyFixer<PyStringLiteralExpression> {
  public PyStringLiteralFixer() {
    super(PyStringLiteralExpression.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyStringLiteralExpression psiElement)
    throws IncorrectOperationException {
    final String text = psiElement.getText();
    if (StringUtil.startsWith(text, "\"\"\"")) {
      final int suffixLength = StringUtil.commonSuffixLength(text, "\"\"\"");
      if (suffixLength != 3) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"\"\"".substring(suffixLength));
      }
    }
    else if (StringUtil.startsWith(text, "\'\'\'")) {
      final int suffixLength = StringUtil.commonSuffixLength(text, "\'\'\'");
      if (suffixLength != 3) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'\'\'".substring(suffixLength));
      }
    }
    else if (StringUtil.startsWith(text, "\"")) {
      if (!StringUtil.endsWith(text, "\"")) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
      }
    }
    else if (StringUtil.startsWith(text, "\'")) {
      if (!StringUtil.endsWith(text, "\'")) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'");
      }
    }
  }
}
