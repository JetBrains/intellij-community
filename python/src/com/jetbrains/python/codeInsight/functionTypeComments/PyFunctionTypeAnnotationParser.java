/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.functionTypeComments;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.documentation.doctest.PyDocstringTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationParser extends PyParser {
  @Override
  protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return new ParsingContext(builder, languageLevel, futureFlag) {
      private final StatementParsing myStatementParsing = new AnnotationParser(this, futureFlag);
      private final ExpressionParsing myExpressionParsing = new ExpressionParsing(this) {
        @Override
        protected IElementType getReferenceType() {
          return PyDocstringTokenTypes.DOC_REFERENCE;
        }
      };

      @Override
      public StatementParsing getStatementParser() {
        return myStatementParsing;
      }

      @Override
      public ExpressionParsing getExpressionParser() {
        return myExpressionParsing;
      }
    };
  }

  private static class AnnotationParser extends StatementParsing {
    public AnnotationParser(ParsingContext context, @Nullable FUTURE futureFlag) {
      super(context, futureFlag);
    }

    @Override
    public void parseStatement() {
      if (myBuilder.eof()) return;
      parseFunctionType();
    }

    private void parseFunctionType() {
      if (atToken(PyTokenTypes.LPAR)) {
        final PsiBuilder.Marker funcTypeMark = myBuilder.mark();
        parseParameterTypeList();
        checkMatches(PyTokenTypes.RARROW, "'->' expected");
        final boolean parsed = getExpressionParser().parseSingleExpression(false);
        if (!parsed) {
          myBuilder.error("expression expected");
        }
        funcTypeMark.done(PyFunctionTypeAnnotationElementTypes.FUNCTION_SIGNATURE);
      }
      recoverUntilMatches("unexpected tokens");
    }

    private void parseParameterTypeList() {
      assert atToken(PyTokenTypes.LPAR);
      final PsiBuilder.Marker listMark = myBuilder.mark();
      myBuilder.advanceLexer();
      
      final ExpressionParsing exprParser = getExpressionParser();
      int paramCount = 0;
      while (!(atAnyOfTokens(PyTokenTypes.RPAR, PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK) || myBuilder.eof()) ) {
        if (paramCount > 0) {
          checkMatches(PyTokenTypes.COMMA, "',' expected");
        }
        boolean parsed;
        if (atToken(PyTokenTypes.MULT)) {
          final PsiBuilder.Marker starMarker = myBuilder.mark();
          myBuilder.advanceLexer();
          parsed = exprParser.parseSingleExpression(false);
          starMarker.done(PyElementTypes.STAR_EXPRESSION);
        }
        else if (atToken(PyTokenTypes.EXP)) {
          final PsiBuilder.Marker doubleStarMarker = myBuilder.mark();
          myBuilder.advanceLexer();
          parsed = exprParser.parseSingleExpression(false);
          doubleStarMarker.done(PyElementTypes.DOUBLE_STAR_EXPRESSION);
        }
        else {
          parsed = exprParser.parseSingleExpression(false);
        }
        if (!parsed) {
          myBuilder.error("expression expected");
          recoverUntilMatches("expression expected", PyTokenTypes.COMMA, PyTokenTypes.RPAR, PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK);
        }
        paramCount++;
      }
      checkMatches(PyTokenTypes.RPAR, "')' expected");
      listMark.done(PyFunctionTypeAnnotationElementTypes.PARAMETER_TYPE_LIST);
    }

    private void recoverUntilMatches(@NotNull String errorMessage, @NotNull IElementType... types) {
      final PsiBuilder.Marker errorMarker = myBuilder.mark();
      boolean hasNonWhitespaceTokens = false;
      while (!(atAnyOfTokens(types) || myBuilder.eof())) {
        // Regular whitespace tokens are already skipped by advancedLexer() 
        if (!atToken(PyTokenTypes.STATEMENT_BREAK)) {
          hasNonWhitespaceTokens = true;
        }
        myBuilder.advanceLexer();
      }
      if (hasNonWhitespaceTokens) {
        errorMarker.error(errorMessage);
      }
      else {
        errorMarker.drop();
      }
    }
  }
}
