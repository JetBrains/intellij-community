package com.intellij.ide.highlighter.custom;

import com.intellij.ide.highlighter.custom.tokens.*;
import com.intellij.psi.CustomHighlighterTokenType;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author dsl
 */
public final class CustomFileTypeLexer extends AbstractCustomLexer {
  private final SyntaxTable myTable;

  public CustomFileTypeLexer(SyntaxTable table) {
    super(buildTokenParsers(table));
    myTable = table;
  }


  private static TokenParser[] buildTokenParsers(SyntaxTable table) {
    final WhitespaceParser whitespaceParser = new WhitespaceParser();
    final LineCommentParser lineCommentParser = LineCommentParser.create(table.getLineComment());
    final MultilineCommentParser multilineCommentParser =
            MultilineCommentParser.create(table.getStartComment(), table.getEndComment());
    final NumberParser numberParser = new NumberParser(table.getNumPostfixChars(), table.isIgnoreCase());
    final HexNumberParser hexNumberParser = HexNumberParser.create(table.getHexPrefix());
    final KeywordParser keywordParser = new KeywordParser(
                    new Set[]{table.getKeywords1(), table.getKeywords2(), table.getKeywords3(), table.getKeywords4()},
                    table.isIgnoreCase());
    final IdentifierParser identifierParser = new IdentifierParser();

    final QuotedStringParser quotedStringParser = new QuotedStringParser("\"");

    final QuotedStringParser quotedStringParser2 = new QuotedStringParser("\'");

    ArrayList<TokenParser> tokenParsers = new ArrayList<TokenParser>();
    tokenParsers.add(whitespaceParser);
    tokenParsers.add(quotedStringParser);
    tokenParsers.add(quotedStringParser2);
    if (lineCommentParser != null) {
      tokenParsers.add(lineCommentParser);
    }
    if (multilineCommentParser != null) {
      tokenParsers.add(multilineCommentParser);
    }
    tokenParsers.add(numberParser);
    if (hexNumberParser != null) {
      tokenParsers.add(hexNumberParser);
    }
    tokenParsers.add(keywordParser);
    tokenParsers.add(identifierParser);

    if (table.isHasBraces()) {
      tokenParsers.add(new BraceTokenParser("{", CustomHighlighterTokenType.L_BRACE));
      tokenParsers.add(new BraceTokenParser("}", CustomHighlighterTokenType.R_BRACE));
    }

    if (table.isHasParens()) {
      tokenParsers.add(new BraceTokenParser("(", CustomHighlighterTokenType.L_PARENTH));
      tokenParsers.add(new BraceTokenParser(")", CustomHighlighterTokenType.R_PARENTH));
    }

    if (table.isHasBrackets()) {
      tokenParsers.add(new BraceTokenParser("[", CustomHighlighterTokenType.L_BRACKET));
      tokenParsers.add(new BraceTokenParser("]", CustomHighlighterTokenType.R_BRACKET));
    }

    final TokenParser[] tokenParserArray = (TokenParser[]) tokenParsers.toArray(new TokenParser[tokenParsers.size()]);
    return tokenParserArray;
  }


}
