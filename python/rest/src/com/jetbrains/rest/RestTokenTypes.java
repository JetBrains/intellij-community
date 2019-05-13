/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest;

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

  RestElementType REST_INJECTION = new RestElementType("REST_INJECTION");
  RestElementType REST_DJANGO_INJECTION = new RestElementType("REST_DJANGO_INJECTION");
  RestElementType DJANGO_LINE = new RestElementType("DJANGO_LINE");
  RestElementType PYTHON_LINE = new RestElementType("PYTHON_LINE");
  RestElementType JAVASCRIPT_LINE = new RestElementType("JAVASCRIPT_LINE");
  RestElementType DIRECT_HYPERLINK = new RestElementType("DIRECT_HYPERLINK");
}

