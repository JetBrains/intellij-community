package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.console.parsing.PyConsoleParser;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class IpnbPyParser extends PyParser {
  private boolean myIPythonStartSymbol;

  @Override
  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new IpnbPyParsingContext(builder, languageLevel, futureFlag, myIPythonStartSymbol, new PythonConsoleData());
  }

  @NotNull
  @Override
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();

    myIPythonStartSymbol = PyConsoleParser.startsWithIPythonSpecialSymbol(builder);

    ParsingContext context = createParsingContext(builder, myLanguageLevel, null);

    StatementParsing statementParser = context.getStatementParser();
    builder.setTokenTypeRemapper(statementParser);

    while (!builder.eof()) {
      statementParser.parseStatement();
    }
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }
}
