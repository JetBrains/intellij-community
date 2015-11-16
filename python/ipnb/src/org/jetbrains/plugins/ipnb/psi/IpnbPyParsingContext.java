package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.console.parsing.PyConsoleParsingContext;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.FunctionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class IpnbPyParsingContext extends PyConsoleParsingContext {
  private final StatementParsing myStatementParser;
  private final ExpressionParsing myExpressionParser;
  private final FunctionParsing myFunctionParser;

  public IpnbPyParsingContext(final PsiBuilder builder,
                              LanguageLevel languageLevel,
                              StatementParsing.FUTURE futureFlag, boolean startSymbol, PythonConsoleData data) {
    super(builder, languageLevel, futureFlag, data, startSymbol);
    myStatementParser = new IpnbPyStatementParsing(this, futureFlag);
    myExpressionParser = new IpnbPyExpressionParsing(this);
    myFunctionParser = new IpnbPyFunctionParsing(this);
  }

  @Override
  public ExpressionParsing getExpressionParser() {
    return myExpressionParser;
  }

  @Override
  public StatementParsing getStatementParser() {
    return myStatementParser;
  }

  @Override
  public FunctionParsing getFunctionParser() {
    return myFunctionParser;
  }

  private static class IpnbPyExpressionParsing extends ConsoleExpressionParsing {
    public IpnbPyExpressionParsing(ParsingContext context) {
      super(context);
    }

    @Override
    protected IElementType getReferenceType() {
      return IpnbPyTokenTypes.IPNB_REFERENCE;
    }

    public boolean parsePrimaryExpression(boolean isTargetExpression) {
      final IElementType firstToken = myBuilder.getTokenType();
      if (firstToken == PyTokenTypes.IDENTIFIER) {
        if (isTargetExpression) {
          buildTokenElement(IpnbPyTokenTypes.IPNB_TARGET, myBuilder);
        }
        else {
          buildTokenElement(getReferenceType(), myBuilder);
        }
        return true;
      }
      return super.parsePrimaryExpression(isTargetExpression);
    }
  }

  private static class IpnbPyStatementParsing extends StatementParsing {

    protected IpnbPyStatementParsing(ParsingContext context, @Nullable FUTURE futureFlag) {
      super(context, futureFlag);
    }

    @Override
    protected IElementType getReferenceType() {
      return IpnbPyTokenTypes.IPNB_REFERENCE;
    }

    @Override
    public void parseStatement() {
      if (myBuilder.getTokenType() == PyTokenTypes.PERC) {
        PsiBuilder.Marker ipythonCommand = myBuilder.mark();
        while (myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK) {
          myBuilder.advanceLexer();
        }
        ipythonCommand.done(PyElementTypes.EMPTY_EXPRESSION);
      }
      super.parseStatement();
    }
  }

  private static class IpnbPyFunctionParsing extends FunctionParsing {

    public IpnbPyFunctionParsing(ParsingContext context) {
      super(context);
    }
    protected IElementType getFunctionType() {
      return IpnbPyTokenTypes.IPNB_FUNCTION;
    }

  }
}
