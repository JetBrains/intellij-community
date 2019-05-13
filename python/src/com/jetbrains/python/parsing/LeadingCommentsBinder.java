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
package com.jetbrains.python.parsing;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

import java.util.List;

/**
 * @author yole
 */
public class LeadingCommentsBinder implements WhitespacesAndCommentsBinder {
  public static final LeadingCommentsBinder INSTANCE = new LeadingCommentsBinder();

  @Override
  public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
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
