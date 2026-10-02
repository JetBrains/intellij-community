// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public class PyDoctestParser extends PyParser {
  @Override
  protected ParsingContext createParsingContext(SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    return new PyDoctestParsingContext(builder, languageLevel);
  }
}
