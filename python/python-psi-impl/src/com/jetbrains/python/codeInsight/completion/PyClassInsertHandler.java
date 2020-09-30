// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class PyClassInsertHandler implements InsertHandler<LookupElement> {
  public static PyClassInsertHandler INSTANCE = new PyClassInsertHandler();

  private PyClassInsertHandler() {
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    if (context.getCompletionChar() == '(') {
      context.setAddCompletionChar(false);
      final int offset = context.getTailOffset();
      document.insertString(offset, "()");

      PyClass pyClass = PyUtil.as(item.getPsiElement(), PyClass.class);
      PyFunction init = pyClass != null ? pyClass.findInitOrNew(true, null) : null;
      if (init != null && PyFunctionInsertHandler.hasParams(context, init)) {
        editor.getCaretModel().moveToOffset(offset+1);
        AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), init);
      }
      else {
        editor.getCaretModel().moveToOffset(offset+2);
      }
    }
  }
}
