package com.jetbrains.python.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyParsingBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for lazily-reparsed {@code PyStatementList} elements.
 * <p>
 * Unlike {@link PyParser}, this class does not implement {@link com.intellij.lang.PsiParser}
 * and does not carry mutable language-level state. All context (language level, builder)
 * is passed explicitly through method parameters.
 */
public final class PyLazyParser {

  private static final Logger LOG = Logger.getInstance(PyLazyParser.class);

  private static class LazyStatementParsing extends StatementParsing {

    LazyStatementParsing(ParsingContext context) {
      super(context);
    }

    void lazyParseStatementList() {
      boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
      if (indentFound) {
        myBuilder.advanceLexer();

        if (myBuilder.eof()) {
          myBuilder.error(PyParsingBundle.message("expected.statement"));
        }

        while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
          parseStatement();
        }

        if (!myBuilder.eof()) {
          if (myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
            LOG.error("Expected DEDENT token, got " + myBuilder.getTokenType());
          }
          myBuilder.advanceLexer();
        }
      }
      else {
        if (myBuilder.eof()) {
          myBuilder.error(PyParsingBundle.message("expected.statement"));
        }
        else {
          final ParsingContext context = getParsingContext();
          context.pushScope(context.getScope().withSuite());
          parseSimpleStatement();
          context.popScope();
          while (matchToken(PyTokenTypes.SEMICOLON)) {
            if (matchToken(PyTokenTypes.STATEMENT_BREAK)) {
              break;
            }
            context.pushScope(context.getScope().withSuite());
            parseSimpleStatement();
            context.popScope();
          }
        }
      }
    }
  }

  public static void parseStatementList(IElementType root, SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    final SyntaxTreeBuilder.Marker rootMarker = builder.mark();
    ParsingContext context = new ParsingContext(builder, languageLevel);
    StatementParsing statementParser = context.getStatementParser();

    builder.setTokenTypeRemapper(statementParser);
    context.pushScope(context.emptyParsingScope());
    LazyStatementParsing lazyStatementParsing = new LazyStatementParsing(context);
    lazyStatementParsing.lazyParseStatementList();

    rootMarker.done(root);
    rootMarker.setCustomEdgeTokenBinders(LeadingCommentsBinder.INSTANCE, FollowingCommentBinder.INSTANCE);
  }

  public @Nullable ASTNode parseLazyElement(@NotNull IElementType rootElement,
                                            @NotNull PsiBuilder builder,
                                            @NotNull LanguageLevel languageLevel) {
    long start = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
    parseStatementList(rootElement, builder, languageLevel);
    try {
      if (!builder.eof()) {
        LOG.debug("Lazy parseable element of type " + rootElement + " ends before EOF");
        return null;
      }
      ASTNode ast = builder.getTreeBuilt();
      return ast.getFirstChildNode();
    }
    finally {
      if (LOG.isDebugEnabled()) {
        long diff = System.currentTimeMillis() - start;
        double kb = builder.getCurrentOffset() / 1000.0;
        LOG.debug("Parsed " + String.format("%.1f", kb) + "K in " + diff + "ms");
      }
    }
  }
}
