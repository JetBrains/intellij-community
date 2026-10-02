// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class OverwriteEqualsInsertHandler implements InsertHandler<LookupElement> {
  public static final OverwriteEqualsInsertHandler INSTANCE = new OverwriteEqualsInsertHandler();

  private OverwriteEqualsInsertHandler() { }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) {
      return;
    }
    String lookupString = item.getLookupString();
    Document doc = context.getDocument();
    int tailOffset = context.getTailOffset();
    if (lookupString.endsWith("=") && CharArrayUtil.regionMatches(doc.getCharsSequence(), tailOffset, "=")) {
      doc.deleteString(tailOffset, tailOffset + 1);
    }
    else if (lookupString.endsWith(" = ") && CharArrayUtil.regionMatches(doc.getCharsSequence(), tailOffset, " = ")) {
      doc.deleteString(tailOffset, tailOffset + 3);
    }
  }
}
