// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OverwriteEqualsInsertHandler implements InsertHandler<LookupElement> {
  public static OverwriteEqualsInsertHandler INSTANCE = new OverwriteEqualsInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) {
      return;
    }
    Document doc = context.getDocument();
    int tailOffset = context.getTailOffset();
    if (tailOffset < doc.getCharsSequence().length() && doc.getCharsSequence().charAt(tailOffset) == '=') {
      doc.deleteString(tailOffset, tailOffset+1);
    }
  }
}
