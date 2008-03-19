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

  public void parseFunctionDeclaration() {
    assertCurrentToken(PyTokenTypes.DEF_KEYWORD);
    final PsiBuilder.Marker functionMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      myBuilder.advanceLexer();
    }
    else {
      myBuilder.error("function name expected");
    }

    parseParameterList();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    getStatementParser().parseSuite(functionMarker, PyElementTypes.FUNCTION_DECLARATION);
  }

  public void parseDecoratedFunctionDeclaration() {
    assertCurrentToken(PyTokenTypes.AT);
    final PsiBuilder.Marker functionMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    getStatementParser().parseDottedName();
    if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
      getExpressionParser().parseArgumentList(myBuilder);
    }
    checkMatches(PyTokenTypes.STATEMENT_BREAK, "statement break expected");
    if (myBuilder.getTokenType() == PyTokenTypes.AT) {
      parseDecoratedFunctionDeclaration();
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
      parseFunctionDeclaration();
    }
    else {
      myBuilder.error("'def' or '@' expected");
    }
    functionMarker.done(PyElementTypes.DECORATED_FUNCTION_DECLARATION);
  }

  private void parseParameterList() {
    final PsiBuilder.Marker parameterList;
    if (myBuilder.getTokenType() != PyTokenTypes.LPAR) {
      myBuilder.error("( expected");
      parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(PyElementTypes.PARAMETER_LIST);
      return;
    }
    parseParameterListContents(PyTokenTypes.RPAR, true);
  }

  public void parseParameterListContents(IElementType endToken, boolean advanceLexer) {
    PsiBuilder.Marker parameterList;
    parameterList = myBuilder.mark();
    if (advanceLexer) {
      myBuilder.advanceLexer();
    }

    boolean first = true;
    while (myBuilder.getTokenType() != endToken) {
      if (first) {
        first = false;
      }
      else {
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
          parseParameterSubList();
        }
        else {
          myBuilder.error(", or ( or ) expected");
          break;
        }
      }

      final PsiBuilder.Marker parameter = myBuilder.mark();
      boolean isStarParameter = false;
      if (myBuilder.getTokenType() == PyTokenTypes.MULT || myBuilder.getTokenType() == PyTokenTypes.EXP) {
        myBuilder.advanceLexer();
        isStarParameter = true;
      }
      if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() == PyTokenTypes.EQ && !isStarParameter) {
          myBuilder.advanceLexer();
          getExpressionParser().parseSingleExpression(false);
        }
        parameter.done(PyElementTypes.FORMAL_PARAMETER);
      }
      else {
        myBuilder.error("formal parameter name expected");
        parameter.rollbackTo();
      }
    }

    if (myBuilder.getTokenType() == endToken) {
      myBuilder.advanceLexer();
    }

    parameterList.done(PyElementTypes.PARAMETER_LIST);
  }

  private void parseParameterSubList() {
    assertCurrentToken(PyTokenTypes.LPAR);
    myBuilder.advanceLexer();
    while (true) {
      if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
        final PsiBuilder.Marker parameter = myBuilder.mark();
        myBuilder.advanceLexer();
        parameter.done(PyElementTypes.FORMAL_PARAMETER);
      }
      else if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
        parseParameterSubList();
      }
      if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
        myBuilder.advanceLexer();
        break;
      }
      if (myBuilder.getTokenType() != PyTokenTypes.COMMA) {
        myBuilder.error(", or ( or ) expected");
        break;
      }
      myBuilder.advanceLexer();
    }
  }
}
