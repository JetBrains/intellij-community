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
package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PyBundle.message;

/**
 * @author yole
 */
public class FunctionParsing extends Parsing {
  private static final IElementType FUNCTION_TYPE = PyElementTypes.FUNCTION_DECLARATION;

  public FunctionParsing(ParsingContext context) {
    super(context);
  }

  public void parseFunctionDeclaration(@NotNull PsiBuilder.Marker endMarker, boolean async) {
    assertCurrentToken(PyTokenTypes.DEF_KEYWORD);
    parseFunctionInnards(endMarker, async);
  }

  protected IElementType getFunctionType() {
    return FUNCTION_TYPE;
  }

  protected void parseFunctionInnards(@NotNull PsiBuilder.Marker functionMarker, boolean async) {
    myBuilder.advanceLexer();
    parseIdentifierOrSkip(PyTokenTypes.LPAR);
    parseParameterList();
    parseReturnTypeAnnotation();
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    final ParsingContext context = getParsingContext();
    context.pushScope(context.getScope().withFunction(async));
    getStatementParser().parseSuite(functionMarker, getFunctionType());
    context.popScope();
  }

  public void parseReturnTypeAnnotation() {
    if (myContext.getLanguageLevel().isPy3K() && myBuilder.getTokenType() == PyTokenTypes.RARROW) {
      PsiBuilder.Marker maybeReturnAnnotation = myBuilder.mark();
      nextToken();
      if (!myContext.getExpressionParser().parseSingleExpression(false)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      maybeReturnAnnotation.done(PyElementTypes.ANNOTATION);
    }
  }

  public void parseDecoratedDeclaration() {
    assertCurrentToken(PyTokenTypes.AT); // ??? need this?
    final PsiBuilder.Marker decoratorStartMarker = myBuilder.mark();
    final PsiBuilder.Marker decoListMarker = myBuilder.mark();
    boolean decorated = false;
    while (myBuilder.getTokenType() == PyTokenTypes.AT) {
      PsiBuilder.Marker decoratorMarker = myBuilder.mark();
      myBuilder.advanceLexer();
      getStatementParser().parseDottedName();
      if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
        getExpressionParser().parseArgumentList();
      }
      else { // empty arglist node, so we always have it
        PsiBuilder.Marker argListMarker = myBuilder.mark();
        argListMarker.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, null);
        argListMarker.done(PyElementTypes.ARGUMENT_LIST);
      }
      if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
        decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
        nextToken();
      }
      else {
        myBuilder.error(message("PARSE.expected.statement.break"));
        decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
      }
      decorated = true;
    }
    if (decorated) decoListMarker.done(PyElementTypes.DECORATOR_LIST);
    //else decoListMarker.rollbackTo();
    parseDeclarationAfterDecorator(decoratorStartMarker);
  }

  private void parseDeclarationAfterDecorator(PsiBuilder.Marker endMarker) {
    if (myBuilder.getTokenType() == PyTokenTypes.ASYNC_KEYWORD) {
      myBuilder.advanceLexer();
      parseDeclarationAfterDecorator(endMarker, true);
    }
    else {
      parseDeclarationAfterDecorator(endMarker, false);
    }
  }

  protected void parseDeclarationAfterDecorator(PsiBuilder.Marker endMarker, boolean async) {
    if (myBuilder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
      parseFunctionInnards(endMarker, async);
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.CLASS_KEYWORD) {
      getStatementParser().parseClassDeclaration(endMarker);
    }
    else {
      myBuilder.error(message("PARSE.expected.@.or.def"));
      PsiBuilder.Marker parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(PyElementTypes.PARAMETER_LIST);
      myBuilder.mark().done(PyElementTypes.STATEMENT_LIST); // To have non-empty empty statement list
      endMarker.done(getFunctionType());
    }
  }

  public void parseParameterList() {
    final PsiBuilder.Marker parameterList;
    if (myBuilder.getTokenType() != PyTokenTypes.LPAR) {
      myBuilder.error(message("PARSE.expected.lpar"));
      parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(PyElementTypes.PARAMETER_LIST);
      return;
    }
    parseParameterListContents(PyTokenTypes.RPAR, true, false);
  }

  public void parseParameterListContents(IElementType endToken, boolean advanceLexer, boolean isLambda) {
    PsiBuilder.Marker parameterList;
    parameterList = myBuilder.mark();
    if (advanceLexer) {
      myBuilder.advanceLexer();
    }

    boolean first = true;
    boolean afterStarParameter = false;
    while (myBuilder.getTokenType() != endToken) {
      if (first) {
        first = false;
      }
      else {
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
        }
        else {
          myBuilder.error(message("PARSE.expected.comma.lpar.rpar"));
          break;
        }
      }
      if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
        parseParameterSubList();
        continue;
      }
      boolean isStarParameter = atAnyOfTokens(PyTokenTypes.MULT, PyTokenTypes.EXP);
      if (!parseParameter(endToken, isLambda)) {
        if (afterStarParameter && myContext.getLanguageLevel().isOlderThan(LanguageLevel.PYTHON36)) {
          myBuilder.error("expression expected");
        }
        break;
      }
      if (isStarParameter) {
        afterStarParameter = true;
      }
    }

    if (myBuilder.getTokenType() == endToken && endToken == PyTokenTypes.RPAR) {
      myBuilder.advanceLexer();
    }

    parameterList.done(PyElementTypes.PARAMETER_LIST);

    if (myBuilder.getTokenType() == endToken && endToken == PyTokenTypes.COLON) {
      myBuilder.advanceLexer();
    }
  }

  protected boolean parseParameter(IElementType endToken, boolean isLambda) {
    final PsiBuilder.Marker parameter = myBuilder.mark();
    boolean isStarParameter = false;
    if (myBuilder.getTokenType() == PyTokenTypes.MULT) {
      myBuilder.advanceLexer();
      if (myContext.getLanguageLevel().isPy3K() &&
          (myBuilder.getTokenType() == PyTokenTypes.COMMA) || myBuilder.getTokenType() == endToken) {
        parameter.done(PyElementTypes.SINGLE_STAR_PARAMETER);
        return true;
      }
      isStarParameter = true;
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.EXP) {
      myBuilder.advanceLexer();
      isStarParameter = true;
    }
    if (matchToken(PyTokenTypes.IDENTIFIER)) {
      if (!isLambda) {
        parseParameterAnnotation();
      }
      if (!isStarParameter && matchToken(PyTokenTypes.EQ)) {
        if (!getExpressionParser().parseSingleExpression(false)) {
          PsiBuilder.Marker invalidElements = myBuilder.mark();
          while(!atAnyOfTokens(endToken, PyTokenTypes.LINE_BREAK, PyTokenTypes.COMMA, null)) {
            nextToken();
          }
          invalidElements.error(message("PARSE.expected.expression"));
        }
      }
      parameter.done(PyElementTypes.NAMED_PARAMETER);
    }
    else {
      parameter.rollbackTo();
      if (atToken(endToken)) {
        return false;
      }
      PsiBuilder.Marker invalidElements = myBuilder.mark();
      while (!atToken(endToken) && !atAnyOfTokens(PyTokenTypes.LINE_BREAK, PyTokenTypes.COMMA, null)) {
        nextToken();
      }
      invalidElements.error(message("PARSE.expected.formal.param.name"));
      return atToken(endToken) || atToken(PyTokenTypes.COMMA);
    }
    return true;
  }

  public void parseParameterAnnotation() {
    if (myContext.getLanguageLevel().isPy3K() && atToken(PyTokenTypes.COLON)) {
      PsiBuilder.Marker annotationMarker = myBuilder.mark();
      nextToken();
      if (!getExpressionParser().parseSingleExpression(false)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      annotationMarker.done(PyElementTypes.ANNOTATION);
    }
  }

  private void parseParameterSubList() {
    assertCurrentToken(PyTokenTypes.LPAR);
    final PsiBuilder.Marker tuple = myBuilder.mark();
    myBuilder.advanceLexer();
    while (true) {
      if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
        final PsiBuilder.Marker parameter = myBuilder.mark();
        myBuilder.advanceLexer();
        parameter.done(PyElementTypes.NAMED_PARAMETER);
      }
      else if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
        parseParameterSubList();
      }
      if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
        myBuilder.advanceLexer();
        break;
      }
      if (myBuilder.getTokenType() != PyTokenTypes.COMMA) {
        myBuilder.error(message("PARSE.expected.comma.lpar.rpar"));
        break;
      }
      myBuilder.advanceLexer();
    }
    if (myBuilder.getTokenType() == PyTokenTypes.EQ) {
      myBuilder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
    }
    tuple.done(PyElementTypes.TUPLE_PARAMETER);
  }
}
