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
        getExpressionParser().parseArgumentList(myBuilder);
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
          myBuilder.error(message("PARSE.expected.comma.lpar.rpar"));
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
        parameter.done(PyElementTypes.NAMED_PARAMETER);
      }
      else {
        myBuilder.error(message("PARSE.expected.formal.param.name"));
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
    tuple.done(PyElementTypes.TUPLE_PARAMETER);
  }
}
