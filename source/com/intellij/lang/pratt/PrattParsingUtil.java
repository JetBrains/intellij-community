/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PrattParsingUtil {

  public static void skipUntil(PrattBuilder builder, @NotNull PrattTokenType type) {
    if (!builder.assertToken(type)) {
      while (!builder.checkToken(type) && !builder.isEof()) {
        builder.advance();
      }
    }
  }
}
