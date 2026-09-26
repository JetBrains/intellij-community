// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.embedding;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.TreeUtil;

public final class EmbeddingUtil {
  public static int calcBaseIndent(ASTNode chameleon) {
    final ASTNode prevLeaf = TreeUtil.prevLeaf(chameleon, true);
    if (prevLeaf == null) {
      return 0;
    }

    final CharSequence text = prevLeaf.getChars();
    int offset = text.length();

    int answer = 0;

    while (--offset >= 0) {
      final char c = text.charAt(offset);
      if (c == '\n') {
        break;
      }
      if (c != ' ' && c != '\t') {
        answer = 0;
        break;
      }
      answer++;
    }
    return answer;
  }
}
