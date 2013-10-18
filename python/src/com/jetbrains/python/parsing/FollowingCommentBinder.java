package com.jetbrains.python.parsing;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

import java.util.List;

/**
* @author yole
*/
class FollowingCommentBinder implements WhitespacesAndCommentsBinder {
  static final FollowingCommentBinder INSTANCE = new FollowingCommentBinder();

  @Override
  public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
    int pos = 0;
    // TODO[yole] handle more cases?
    while (pos < tokens.size() && tokens.get(pos) == PyTokenTypes.LINE_BREAK) {
      final CharSequence charSequence = getter.get(pos);
      if (charSequence.length() == 0 || charSequence.charAt(charSequence.length()-1) != ' ') {
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
