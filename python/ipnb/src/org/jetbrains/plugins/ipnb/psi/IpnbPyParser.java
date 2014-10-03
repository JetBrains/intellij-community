package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.PsiBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;

public class IpnbPyParser extends PyParser {
  @Override
  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new IpnbPyParsingContext(builder, languageLevel, futureFlag);
  }
}
