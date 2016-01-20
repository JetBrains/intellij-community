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
package com.intellij.embedding;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class EmbeddingUtil {
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
