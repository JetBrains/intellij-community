// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public class PyDoctestParsingContext extends ParsingContext {
  private final StatementParsing myStatementParser;
  private final ExpressionParsing myExpressionParser;

  public PyDoctestParsingContext(final SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    super(builder, languageLevel);
    myStatementParser = new PyDocstringStatementParsing(this);
    myExpressionParser = new PyDocstringExpressionParsing(this);
  }

  @Override
  public ExpressionParsing getExpressionParser() {
    return myExpressionParser;
  }

  @Override
  public StatementParsing getStatementParser() {
    return myStatementParser;
  }

  private static class PyDocstringExpressionParsing extends ExpressionParsing {
    PyDocstringExpressionParsing(ParsingContext context) {
      super(context);
    }

    @Override
    protected IElementType getReferenceType() {
      return PyDoctestTokenTypes.DOC_REFERENCE;
    }
  }

  private static class PyDocstringStatementParsing extends StatementParsing {

    protected PyDocstringStatementParsing(ParsingContext context) {
      super(context);
    }

    @Override
    protected IElementType getReferenceType() {
      return PyDoctestTokenTypes.DOC_REFERENCE;
    }

    @Override
    public @NotNull IElementType filter(@NotNull IElementType source, int start, int end, @NotNull CharSequence text) {
      if (source == PyTokenTypes.DOT && CharArrayUtil.regionMatches(text, start, end, "..."))
        return PyDoctestTokenTypes.DOTS;
      if (source == PyTokenTypes.GTGT && CharArrayUtil.regionMatches(text, start, end, ">>>"))
        return PyTokenTypes.SPACE;
      return super.filter(source, start, end, text);
    }
  }
}
