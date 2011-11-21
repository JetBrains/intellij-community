package com.jetbrains.python.console.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyConsoleParser extends PyParser{
  private LanguageLevel myLanguageLevel;
  private StatementParsing.FUTURE myFutureFlag;
  private PsiElement myPsi;
  private boolean myIPythonStartSymbol;

  public PyConsoleParser(PsiElement psi) {
    myPsi = psi;
    myLanguageLevel = LanguageLevel.getDefault();
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  @NotNull
  @Override
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    long start = System.currentTimeMillis();
    final PsiBuilder.Marker rootMarker = builder.mark();

    myIPythonStartSymbol = startsWithIPythonSpecialSymbol(builder);

    ParsingContext context = createParsingContext(builder, myLanguageLevel, myFutureFlag);

    StatementParsing stmt_parser = context.getStatementParser();
    builder.setTokenTypeRemapper(stmt_parser); // must be done before touching the caching lexer with eof() call.
    //if (builder.getTokenType() == PyTokenTypes.BACKSLASH)

    while (!builder.eof()) {
      stmt_parser.parseStatement(context.emptyParsingScope());
    }
    rootMarker.done(root);
    ASTNode ast = builder.getTreeBuilt();
    long diff = System.currentTimeMillis() - start;
    double kb = builder.getCurrentOffset() / 1000.0;
    return ast;
  }

  private static boolean startsWithIPythonSpecialSymbol(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    return builder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK || tokenType == PyTokenTypes.PERC;
  }


  @Override
  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new PyConsoleParsingContext(builder, languageLevel, futureFlag, myPsi, myIPythonStartSymbol);
  }
}
