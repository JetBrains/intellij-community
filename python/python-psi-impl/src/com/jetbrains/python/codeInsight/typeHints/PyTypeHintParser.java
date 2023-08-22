// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typeHints;

import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.documentation.doctest.PyDocstringTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

class PyTypeHintParser extends PyParser {

  @Override
  @NotNull
  protected ParsingContext createParsingContext(@NotNull SyntaxTreeBuilder builder, @NotNull LanguageLevel languageLevel) {
    return new ParsingContext(builder, languageLevel) {

      @NotNull
      private final StatementParsing myStatementParsing = new StatementParsing(this) {
        @Override
        @NotNull
        protected IElementType getReferenceType() {
          return PyDocstringTokenTypes.DOC_REFERENCE;
        }
      };

      @NotNull
      private final ExpressionParsing myExpressionParsing = new ExpressionParsing(this) {
        @Override
        @NotNull
        protected IElementType getReferenceType() {
          return PyDocstringTokenTypes.DOC_REFERENCE;
        }
      };

      @Override
      @NotNull
      public StatementParsing getStatementParser() {
        return myStatementParsing;
      }

      @Override
      @NotNull
      public ExpressionParsing getExpressionParser() {
        return myExpressionParsing;
      }
    };
  }
}
