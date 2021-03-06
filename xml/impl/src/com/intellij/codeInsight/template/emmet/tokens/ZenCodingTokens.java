// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet.tokens;

/**
 * @author Eugene.Kudelevsky
 */
public final class ZenCodingTokens {
  private ZenCodingTokens() {
  }

  public static final ZenCodingToken CLOSING_R_BRACKET = new ZenCodingTokenImpl(")");
  public static final ZenCodingToken OPENING_R_BRACKET = new ZenCodingTokenImpl("(");
  public static final ZenCodingToken OPENING_SQ_BRACKET = new ZenCodingTokenImpl("[");
  public static final ZenCodingToken CLOSING_SQ_BRACKET = new ZenCodingTokenImpl("]");
  public static final ZenCodingToken PIPE = new ZenCodingTokenImpl("|");
  public static final ZenCodingToken MARKER = new ZenCodingTokenImpl("&");
  public static final ZenCodingToken EQ = new ZenCodingTokenImpl("=");
  public static final ZenCodingToken SPACE = new ZenCodingTokenImpl(" ");
  public static final ZenCodingToken COMMA = new ZenCodingTokenImpl(",");
  public static final ZenCodingToken DOT = new ZenCodingTokenImpl(".");
  public static final ZenCodingToken SHARP = new ZenCodingTokenImpl("#");
}
