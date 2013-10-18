package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.PsiBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public class PyDocstringParser extends PyParser {
  @Override
  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new PyDocstringParsingContext(builder, languageLevel, futureFlag);
  }
}
