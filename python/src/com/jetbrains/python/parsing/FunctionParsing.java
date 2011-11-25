package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyBundle.message;

/**
 * @author yole
 */
public class FunctionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.FunctionParsing");
  private static final IElementType FUNCTION_TYPE = PyElementTypes.FUNCTION_DECLARATION;
  private static final IElementType PARAMETER_LIST_TYPE = PyElementTypes.PARAMETER_LIST;

  public FunctionParsing(ParsingContext context) {
    super(context);
  }

  public void parseFunctionDeclaration() {
    assertCurrentToken(PyTokenTypes.DEF_KEYWORD);
    final PsiBuilder.Marker functionMarker = myBuilder.mark();
    parseFunctionInnards(functionMarker);
  }

  protected IElementType getFunctionType() {
    return FUNCTION_TYPE;
  }

  protected IElementType getParameterListType() {
    return PARAMETER_LIST_TYPE;
  }

  @Nullable
  protected IElementType getNameType() {
    return null;
  }

  protected void parseFunctionInnards(PsiBuilder.Marker functionMarker) {
    myBuilder.advanceLexer();
    final IElementType elementType = getNameType();
    final PsiBuilder.Marker mark = myBuilder.mark();
    boolean nameFound = false;
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      myBuilder.advanceLexer();
      nameFound = true;
    }
    else {
      myBuilder.error(message("PARSE.expected.func.name"));
    }
    parseParameterList();
    parseReturnTypeAnnotation();
    if (elementType != null && nameFound) {
      mark.done(elementType);
    }
    else {
      mark.drop();
    }
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    getStatementParser().parseSuite(functionMarker, getFunctionType(), myContext.emptyParsingScope().withFunction(true));
  }

  public void parseReturnTypeAnnotation() {
    if (myContext.getLanguageLevel().isPy3K() && myBuilder.getTokenType() == PyTokenTypes.MINUS) {
      PsiBuilder.Marker maybeReturnAnnotation = myBuilder.mark();
      nextToken();
      if (matchToken(PyTokenTypes.GT)) {
        if (!myContext.getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        maybeReturnAnnotation.done(PyElementTypes.ANNOTATION);
      }
      else {
        maybeReturnAnnotation.rollbackTo();
      }
    }
  }

  public void parseDecoratedDeclaration(ParsingScope scope) {
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
        myBuilder.mark().done(PyElementTypes.ARGUMENT_LIST);
      }
      checkMatches(PyTokenTypes.STATEMENT_BREAK, message("PARSE.expected.statement.break"));
      decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
      decorated = true;
    }
    if (decorated) decoListMarker.done(PyElementTypes.DECORATOR_LIST);
    //else decoListMarker.rollbackTo();
    parseDeclarationAfterDecorator(decoratorStartMarker, scope);
  }

  protected void parseDeclarationAfterDecorator(PsiBuilder.Marker endMarker, ParsingScope scope) {
    if (myBuilder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
      parseFunctionInnards(endMarker); // it calls endMarker.done()
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.CLASS_KEYWORD) {
      getStatementParser().parseClassDeclaration(endMarker, scope);
    }
    else {
      myBuilder.error(message("PARSE.expected.@.or.def"));
      PsiBuilder.Marker parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(getParameterListType());
      endMarker.done(getFunctionType());
    }
  }

  public void parseParameterList() {
    final PsiBuilder.Marker parameterList;
    if (myBuilder.getTokenType() != PyTokenTypes.LPAR) {
      myBuilder.error(message("PARSE.expected.lpar"));
      parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(getParameterListType());
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
        if (afterStarParameter) {
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

    parameterList.done(getParameterListType());

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
      if (!isLambda && myContext.getLanguageLevel().isPy3K() && atToken(PyTokenTypes.COLON)) {
        PsiBuilder.Marker annotationMarker = myBuilder.mark();
        nextToken();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        annotationMarker.done(PyElementTypes.ANNOTATION);
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
      while (!atToken(endToken) && !atToken(PyTokenTypes.LINE_BREAK) && !atToken(PyTokenTypes.COMMA) && !atToken(null)) {
        nextToken();
      }
      invalidElements.error(message("PARSE.expected.formal.param.name"));
      return false;
    }
    return true;
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
