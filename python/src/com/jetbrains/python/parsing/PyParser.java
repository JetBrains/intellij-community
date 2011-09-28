package com.jetbrains.python.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyParser implements PsiParser {
  private static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());

  private LanguageLevel myLanguageLevel;
  private StatementParsing.FUTURE myFutureFlag;

  public PyParser() {
    myLanguageLevel = LanguageLevel.getDefault();
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    long start = System.currentTimeMillis();
    final PsiBuilder.Marker rootMarker = builder.mark();
    ParsingContext context = createParsingContext(builder, myLanguageLevel, myFutureFlag);
    StatementParsing stmt_parser = context.getStatementParser();
    builder.setTokenTypeRemapper(stmt_parser); // must be done before touching the caching lexer with eof() call.
    while (!builder.eof()) {
      stmt_parser.parseStatement(context.emptyParsingScope());
    }
    rootMarker.done(root);
    ASTNode ast = builder.getTreeBuilt();
    long diff = System.currentTimeMillis() - start;
    double kb = builder.getCurrentOffset() / 1000.0;
    LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
    return ast;
  }

  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new ParsingContext(builder, languageLevel, futureFlag);
  }

  public void setFutureFlag(StatementParsing.FUTURE future) {
    myFutureFlag = future;
  }
}
