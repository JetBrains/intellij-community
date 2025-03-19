// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

import java.util.List;

class FollowingCommentBinder implements WhitespacesAndCommentsBinder {
  static final FollowingCommentBinder INSTANCE = new FollowingCommentBinder();

  @Override
  public int getEdgePosition(List<? extends IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
    if (tokens.size() <= 1) return 0;
    int pos = 0;
    // TODO[yole] handle more cases?
    while (pos < tokens.size() && tokens.get(pos) == PyTokenTypes.LINE_BREAK) {
      final CharSequence charSequence = getter.get(pos);
      if (charSequence.isEmpty() || charSequence.charAt(charSequence.length() - 1) != ' ') {
        break;
      }
      pos++;
      if (pos == tokens.size() || tokens.get(pos) != PyTokenTypes.END_OF_LINE_COMMENT) {
        break;
      }
      pos++;
    }
    return pos;
  }
}
