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
package com.jetbrains.python;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBraceMatcher implements PairedBraceMatcher {
  private final BracePair[] PAIRS;

  public PyBraceMatcher() {
    PAIRS = new BracePair[]{new BracePair(PyTokenTypes.LPAR, PyTokenTypes.RPAR, false),
      new BracePair(PyTokenTypes.LBRACKET, PyTokenTypes.RBRACKET, false), new BracePair(PyTokenTypes.LBRACE, PyTokenTypes.RBRACE, false)};
  }

  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return
      PyTokenTypes.WHITESPACE_OR_LINEBREAK.contains(contextType) ||
      contextType == PyTokenTypes.END_OF_LINE_COMMENT ||
      contextType == PyTokenTypes.COLON ||
      contextType == PyTokenTypes.COMMA ||
      contextType == PyTokenTypes.RPAR ||
      contextType == PyTokenTypes.RBRACKET ||
      contextType == PyTokenTypes.RBRACE ||
      contextType == PyTokenTypes.LBRACE ||
      contextType == null;
  }

  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
