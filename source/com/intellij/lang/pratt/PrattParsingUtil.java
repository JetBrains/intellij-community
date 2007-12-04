/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PrattParsingUtil {

  public static void skipUntil(PrattBuilder builder, @NotNull PrattTokenType... types) {
    final TokenSet set = TokenSet.create(types);
    if (!set.contains(builder.getTokenType())) {
      builder.assertToken(types[0]);
      while (!set.contains(builder.getTokenType()) && !builder.isEof()) {
        builder.advance();
      }
    }
    builder.advance();
  }
}
