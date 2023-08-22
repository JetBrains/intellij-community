// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;


public class PyParser extends PythonParser implements PsiParser {

  public PyParser() {
    super();
  }

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    long start = System.currentTimeMillis();
    parseRoot(root, builder);
    ASTNode ast = builder.getTreeBuilt();
    if (LOGGER.isDebugEnabled()) {
      long diff = System.currentTimeMillis() - start;
      double kb = builder.getCurrentOffset() / 1000.0;
      if (diff > 5) { // Only log heavy file parsing
        LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
      }
    }
    return ast;
  }
}
