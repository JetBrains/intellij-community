// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

import java.util.List;


public class LeadingCommentsBinder implements WhitespacesAndCommentsBinder {
  public static final LeadingCommentsBinder INSTANCE = new LeadingCommentsBinder();

  @Override
  public int getEdgePosition(List<? extends IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
    if (tokens.size() > 1) {
      boolean seenLF = false;
      for (int i = 0; i < tokens.size(); i++) {
        IElementType token = tokens.get(i);
        if (token == PyTokenTypes.LINE_BREAK) {
          seenLF = true;
        }
        else if (token == PyTokenTypes.END_OF_LINE_COMMENT && seenLF) {
          return i;
        }
      }
    }
    return tokens.size();
  }
}
