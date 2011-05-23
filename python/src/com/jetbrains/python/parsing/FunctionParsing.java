package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

import static com.jetbrains.python.PyBundle.message;

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
    parseFunctionInnards(functionMarker);
  }

  protected void parseFunctionInnards(PsiBuilder.Marker functionMarker) {
    myBuilder.advanceLexer();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      myBuilder.advanceLexer();
    }
    else {
      myBuilder.error(message("PARSE.expected.func.name"));
    }

    parseParameterList();
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
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    getStatementParser().parseSuite(functionMarker, PyElementTypes.FUNCTION_DECLARATION);
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
        myBuilder.mark().done(PyElementTypes.ARGUMENT_LIST);
      }
      checkMatches(PyTokenTypes.STATEMENT_BREAK, message("PARSE.expected.statement.break"));
      decoratorMarker.done(PyElementTypes.DECORATOR_CALL);
      decorated = true;
    }
    if (decorated) decoListMarker.done(PyElementTypes.DECORATOR_LIST);
    //else decoListMarker.rollbackTo(); 
    if (myBuilder.getTokenType() == PyTokenTypes.DEF_KEYWORD) {
      parseFunctionInnards(decoratorStartMarker); // it calls decoratorStartMarker.done()
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.CLASS_KEYWORD) {
      getStatementParser().parseClassDeclaration(decoratorStartMarker);
    }
    else {
      myBuilder.error(message("PARSE.expected.@.or.def"));
      PsiBuilder.Marker parameterList = myBuilder.mark(); // To have non-empty parameters list at all the time.
      parameterList.done(PyElementTypes.PARAMETER_LIST);
      decoratorStartMarker.done(PyElementTypes.FUNCTION_DECLARATION);
    }
  }

  private void parseParameterList() {
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

      final PsiBuilder.Marker parameter = myBuilder.mark();
      boolean isStarParameter = false;
      if (myBuilder.getTokenType() == PyTokenTypes.MULT) {
        myBuilder.advanceLexer();
        if (myContext.getLanguageLevel().isPy3K() &&
            (myBuilder.getTokenType() == PyTokenTypes.COMMA) || myBuilder.getTokenType() == endToken) {
          parameter.done(PyElementTypes.SINGLE_STAR_PARAMETER);
          continue;
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
            myBuilder.error(message("PARSE.expected.expression"));
          }
        }
        parameter.done(PyElementTypes.NAMED_PARAMETER);
      }
      else {
        parameter.rollbackTo();
        PsiBuilder.Marker invalidElements = myBuilder.mark();
        while (!atToken(endToken) && !atToken(PyTokenTypes.LINE_BREAK) && !atToken(PyTokenTypes.COMMA) && !atToken(null)) {
          nextToken();
        }
        invalidElements.error(message("PARSE.expected.formal.param.name"));
        break;
      }
    }

    if (myBuilder.getTokenType() == endToken) {
      myBuilder.advanceLexer();
    }

    parameterList.done(PyElementTypes.PARAMETER_LIST);
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
