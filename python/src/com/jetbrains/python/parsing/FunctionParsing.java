/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class FunctionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.FunctionParsing");

  public FunctionParsing(ParsingContext context) {
    super(context);
  }

  public void parseFunctionDeclaration(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.DEF_KEYWORD);
    final PsiBuilder.Marker functionMarker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      builder.advanceLexer();
    }
    else {
      builder.error("function name expected");
    }

    parseParameterList(builder);
    checkMatches(PyTokenTypes.COLON, "colon expected");
    getStatementParser().parseSuite(builder, functionMarker, PyElementTypes.FUNCTION_DECLARATION);
  }

  public void parseDecoratedFunctionDeclaration(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.AT);
    final PsiBuilder.Marker functionMarker = builder.mark();
    builder.advanceLexer();
    getStatementParser().parseDottedName();
    if (builder.getTokenType() == PyTokenTypes.LPAR) {
      getExpressionParser().parseArgumentList(builder);
    }
    checkMatches(PyTokenTypes.STATEMENT_BREAK, "statement break expected");
    if (builder.getTokenType() == PyTokenTypes.AT) {
      parseDecoratedFunctionDeclaration(builder);
    }
    else if (builder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
      parseFunctionDeclaration(builder);
    }
    else {
      builder.error("'def' or '@' expected");
    }
    functionMarker.done(PyElementTypes.DECORATED_FUNCTION_DECLARATION);
  }

  private void parseParameterList(PsiBuilder builder) {
    final PsiBuilder.Marker parameterList;
    if (builder.getTokenType() != PyTokenTypes.LPAR) {
      builder.error("( expected");
      parameterList = builder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(PyElementTypes.PARAMETER_LIST);
      return;
    }
    parseParameterListContents(builder, PyTokenTypes.RPAR, true);
  }

  public void parseParameterListContents(PsiBuilder builder, IElementType endToken, boolean advanceLexer) {
    PsiBuilder.Marker parameterList;
    parameterList = builder.mark();
    if (advanceLexer) {
      builder.advanceLexer();
    }

    boolean first = true;
    while (builder.getTokenType() != endToken) {
      if (first) {
        first = false;
      }
      else {
        if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
        }
        else if (builder.getTokenType() == PyTokenTypes.LPAR) {
          parseParameterSubList(builder);
        }
        else {
          builder.error(", or ( or ) expected");
          break;
        }
      }

      final PsiBuilder.Marker parameter = builder.mark();
      boolean isStarParameter = false;
      if (builder.getTokenType() == PyTokenTypes.MULT || builder.getTokenType() == PyTokenTypes.EXP) {
        builder.advanceLexer();
        isStarParameter = true;
      }
      if (builder.getTokenType() == PyTokenTypes.IDENTIFIER) {
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.EQ && !isStarParameter) {
          builder.advanceLexer();
          getExpressionParser().parseSingleExpression(builder, false);
        }
        parameter.done(PyElementTypes.FORMAL_PARAMETER);
      }
      else {
        builder.error("formal parameter name expected");
        parameter.rollbackTo();
      }
    }

    if (builder.getTokenType() == endToken) {
      builder.advanceLexer();
    }

    parameterList.done(PyElementTypes.PARAMETER_LIST);
  }

  private void parseParameterSubList(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LPAR);
    builder.advanceLexer();
    while (true) {
      if (builder.getTokenType() == PyTokenTypes.IDENTIFIER) {
        final PsiBuilder.Marker parameter = builder.mark();
        builder.advanceLexer();
        parameter.done(PyElementTypes.FORMAL_PARAMETER);
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        parseParameterSubList(builder);
      }
      if (builder.getTokenType() == PyTokenTypes.RPAR) {
        builder.advanceLexer();
        break;
      }
      if (builder.getTokenType() != PyTokenTypes.COMMA) {
        builder.error(", or ( or ) expected");
        break;
      }
      builder.advanceLexer();
    }
  }
}
