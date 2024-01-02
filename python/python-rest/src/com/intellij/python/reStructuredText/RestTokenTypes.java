// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.OuterLanguageElementType;

/**
 * User : catherine
 */
public interface RestTokenTypes {
  RestElementType WHITESPACE = new RestElementType("WHITESPACE");

  RestElementType TITLE = new RestElementType("TITLE");
  RestElementType TITLE_TEXT = new RestElementType("TITLETEXT");
  RestElementType EXPLISIT_MARKUP_START = new RestElementType("EXPLISIT_MARKUP_START");
  RestElementType FOOTNOTE = new RestElementType("FOOTNOTE");
  RestElementType CITATION = new RestElementType("CITATION");
  RestElementType HYPERLINK = new RestElementType("HYPERLINK");
  RestElementType ANONYMOUS_HYPERLINK = new RestElementType("ANONYMOUS_HYPERLINK");
  RestElementType DIRECTIVE = new RestElementType("DIRECTIVE");
  RestElementType CUSTOM_DIRECTIVE = new RestElementType("CUSTOM_DIRECTIVE");
  RestElementType SUBSTITUTION = new RestElementType("SUBSTITUTION");

  RestElementType LINE = new RestElementType("LINE");
  RestElementType INLINE_LINE = new RestElementType("INLINE_LINE");
  RestElementType SPEC_SYMBOL = new RestElementType("SPEC_SYMBOL");

  RestElementType COMMENT = new RestElementType("COMMENT");
  RestElementType REFERENCE_NAME = new RestElementType("REFERENCE_NAME");

  RestElementType FIELD = new RestElementType("FIELD");

  RestElementType BOLD = new RestElementType("BOLD");
  RestElementType ITALIC = new RestElementType("ITALIC");
  RestElementType FIXED = new RestElementType("FIXED");
  RestElementType LITERAL_BLOCK_START = new RestElementType("LITERAL_BLOCK_START");
  RestElementType INTERPRETED = new RestElementType("INTERPRETED");

  RestElementType ERROR = new RestElementType("ERROR");

  IElementType REST_INJECTION = new OuterLanguageElementType("REST_INJECTION", RestLanguage.INSTANCE);
  IElementType REST_DJANGO_INJECTION = new OuterLanguageElementType("REST_DJANGO_INJECTION", RestLanguage.INSTANCE);
  RestElementType DJANGO_LINE = new RestElementType("DJANGO_LINE");
  RestElementType PYTHON_LINE = new RestElementType("PYTHON_LINE");
  RestElementType JAVASCRIPT_LINE = new RestElementType("JAVASCRIPT_LINE");
  RestElementType DIRECT_HYPERLINK = new RestElementType("DIRECT_HYPERLINK");
}

