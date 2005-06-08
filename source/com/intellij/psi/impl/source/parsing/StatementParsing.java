package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;

public class StatementParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.StatementParsing");

  public StatementParsing(JavaParsingContext context) {
    super(context);
  }

  public CompositeElement parseCodeBlockText(PsiManager manager, char[] buffer) {
    return parseCodeBlockText(manager, null, buffer, 0, buffer.length, -1);
  }

  public CompositeElement parseCodeBlockText(PsiManager manager,
                                             Lexer lexer,
                                             char[] buffer,
                                             int startOffset,
                                             int endOffset,
                                             int state) {
    if (lexer == null){
      lexer = new JavaLexer(myContext.getLanguageLevel());
    }
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    CompositeElement block = Factory.createCompositeElement(CODE_BLOCK);
    TreeUtil.addChildren(dummyRoot, block);
    parseCodeBlockDeep(block, filterLexer, true);
    if (block.getFirstChildNode() == null) return null;

    ParseUtil.insertMissingTokens(block, lexer, startOffset, endOffset, state, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return block;
  }

  public TreeElement parseStatements(PsiManager manager,
                                     Lexer lexer,
                                     char[] buffer,
                                     int startOffset,
                                     int endOffset,
                                     int state) {
    if (lexer == null){
      lexer = new JavaLexer(myContext.getLanguageLevel());
    }
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    parseStatements(dummyRoot, filterLexer, RBRACE_IS_ERROR);

    ParseUtil.insertMissingTokens(dummyRoot,
      lexer,
      startOffset,
      endOffset,
      state,
      ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (TreeElement)dummyRoot.getFirstChildNode();
  }

  public TreeElement parseCodeBlock(Lexer lexer, boolean deep) {
    if (lexer.getTokenType() != LBRACE) return null;
    if (lexer instanceof FilterLexer){
      if (((FilterLexer)lexer).getOriginal() instanceof JavaWithJspTemplateDataLexer){
        deep = true; // deep parsing of code blocks in JSP would lead to incorrect parsing on transforming
      }
    }

    if (!deep){
      int start = lexer.getTokenStart();
      lexer.advance();
      int braceCount = 1;
      int end;
      while(true){
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == null){
          end = lexer.getTokenStart();
          break;
        }
        if (tokenType == LBRACE){
          braceCount++;
        }
        else if (tokenType == RBRACE){
          braceCount--;
        }
        if (braceCount == 0){
          end = lexer.getTokenEnd();
          lexer.advance();
          break;
        }
        lexer.advance();
      }
      final TreeElement chameleon = Factory.createLeafElement(CODE_BLOCK, lexer.getBuffer(), start, end, lexer.getState(), myContext.getCharTable());
      if (braceCount != 0){
        chameleon.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
      }
      return chameleon;
    }
    else{
      CompositeElement codeBlock = Factory.createCompositeElement(CODE_BLOCK);
      parseCodeBlockDeep(codeBlock, lexer, false);
      return codeBlock;
    }
  }

  private void parseCodeBlockDeep(CompositeElement elementToAdd,
                                         Lexer lexer,
                                         boolean parseToEndOfLexer) {
    LOG.assertTrue(lexer.getTokenType() == LBRACE);

    TreeUtil.addChildren(elementToAdd, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    parseStatements(elementToAdd, lexer, parseToEndOfLexer ? LAST_RBRACE_IS_END : RBRACE_IS_END);

    if (elementToAdd.getLastChildNode() == null || elementToAdd.getLastChildNode().getElementType() != RBRACE){
      CompositeElement errorElement = Factory.createErrorElement("'}' expected");
      TreeUtil.addChildren(elementToAdd, errorElement);
      elementToAdd.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
    }
  }

  private static final int RBRACE_IS_ERROR = 1;
  private static final int RBRACE_IS_END = 2;
  private static final int LAST_RBRACE_IS_END = 3;

  private void parseStatements(CompositeElement elementToAdd, Lexer lexer, int rbraceMode){
    while(lexer.getTokenType() != null){
      TreeElement statement = parseStatement(lexer);
      if (statement != null){
        TreeUtil.addChildren(elementToAdd, statement);
        continue;
      }

      IElementType tokenType = lexer.getTokenType();
      TreeElement tokenElement = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      if (tokenType == RBRACE){
        Label:
        if (rbraceMode == RBRACE_IS_ERROR) {
        }
        else if (rbraceMode == RBRACE_IS_END) {
          TreeUtil.addChildren(elementToAdd, tokenElement);
          return;
        }
        else if (rbraceMode == LAST_RBRACE_IS_END) {
          if (lexer.getTokenType() == null) {
            TreeUtil.addChildren(elementToAdd, tokenElement);
            return;
          }
          else {
            break Label;
          }
        }
        else {
          LOG.assertTrue(false);
        }
      }

      String error;
      if (tokenType == ELSE_KEYWORD){
        error = "'else' without 'if'";
      }
      else if (tokenType == CATCH_KEYWORD){
        error = "'catch' without 'try'";
      }
      else if (tokenType == FINAL_KEYWORD){
        error = "'finally' without 'try'";
      }
      else{
        error = "Unexpected token";
      }
      CompositeElement errorElement = Factory.createErrorElement(error);
      TreeUtil.addChildren(errorElement, tokenElement);
      TreeUtil.addChildren(elementToAdd, errorElement);
    }
  }

  public TreeElement parseStatementText(char[] buffer) {
    Lexer lexer = new JavaLexer(myContext.getLanguageLevel());
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, 0, buffer.length);

    TreeElement statement = parseStatement(filterLexer);
    if (statement == null) return null;
    if (filterLexer.getTokenType() != null) return null;

    if(statement instanceof CompositeElement)
      ParseUtil.insertMissingTokens((CompositeElement)statement, lexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return statement;
  }

  public TreeElement parseStatement(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == IF_KEYWORD) {
      return parseIfStatement(lexer);
    }
    else if (tokenType == WHILE_KEYWORD) {
      return parseWhileStatement(lexer);
    }
    else if (tokenType == FOR_KEYWORD) {
      return parseForStatement(lexer);
    }
    else if (tokenType == DO_KEYWORD) {
      return parseDoWhileStatement(lexer);
    }
    else if (tokenType == SWITCH_KEYWORD) {
      return parseSwitchStatement(lexer);
    }
    else if (tokenType == CASE_KEYWORD || tokenType == DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(lexer);
    }
    else if (tokenType == BREAK_KEYWORD) {
      return parseBreakStatement(lexer);
    }
    else if (tokenType == CONTINUE_KEYWORD) {
      return parseContinueStatement(lexer);
    }
    else if (tokenType == RETURN_KEYWORD) {
      return parseReturnStatement(lexer);
    }
    else if (tokenType == THROW_KEYWORD) {
      return parseThrowStatement(lexer);
    }
    else if (tokenType == SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(lexer);
    }
    else if (tokenType == TRY_KEYWORD) {
      return parseTryStatement(lexer);
    }
    else if (tokenType == ASSERT_KEYWORD) {
      return parseAssertStatement(lexer);
    }
    else if (tokenType == LBRACE) {
      return parseBlockStatement(lexer);
    }
    else if (tokenType instanceof IChameleonElementType) {
      LeafElement declaration = Factory.createLeafElement(tokenType, lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState(), myContext.getCharTable());
      lexer.advance();
      return declaration;
    }
    else if (tokenType == SEMICOLON) {
      {
        CompositeElement element = Factory.createCompositeElement(EMPTY_STATEMENT);
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }
    }
    else {
      {
        if (lexer.getTokenType() == IDENTIFIER) {
          final LexerPosition refPos = lexer.getCurrentPosition();
          skipQualifiedName(lexer);
          final IElementType suspectedLT = lexer.getTokenType();
          lexer.restore(refPos);
          LOG.assertTrue(lexer.getTokenType() == IDENTIFIER);
          if (suspectedLT == LT) {
            final TreeElement decl = myContext.getDeclarationParsing().parseDeclaration(lexer,
                                                                                        DeclarationParsing.Context.CODE_BLOCK_CONTEXT);
            CompositeElement declStatement = Factory.createCompositeElement(DECLARATION_STATEMENT);
            if (decl != null) {
              TreeUtil.addChildren(declStatement, decl);
            }
            else {
              final CompositeElement type = parseType(lexer, false, false);
              TreeUtil.addChildren(declStatement, type);
              final CompositeElement errorElement = Factory.createErrorElement("Identifier expected");
              TreeUtil.addChildren(declStatement, errorElement);
            }
            return declStatement;
          }
        }

        final LexerPosition pos = lexer.getCurrentPosition();
        CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
        final LexerPosition pos1 = lexer.getCurrentPosition();
        if (expr != null) {
          int count = 1;
          CompositeElement element = null;
          while (lexer.getTokenType() == COMMA) {
            CompositeElement list = Factory.createCompositeElement(EXPRESSION_LIST);
            element = Factory.createCompositeElement(EXPRESSION_LIST_STATEMENT);
            TreeUtil.addChildren(element, list);
            TreeUtil.addChildren(list, expr);
            final LexerPosition commaPos = lexer.getCurrentPosition();
            TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
            lexer.advance();
            CompositeElement expr1 = myContext.getExpressionParsing().parseExpression(lexer);
            if (expr1 == null) {
              lexer.restore(commaPos);
              break;
            }
            TreeUtil.addChildren(list, comma);
            TreeUtil.addChildren(list, expr1);
            count++;
          }
          if (count > 1) {
            processClosingSemicolon(element, lexer);
            return element;
          }
          if (expr.getElementType() != REFERENCE_EXPRESSION) {
            element = Factory.createCompositeElement(EXPRESSION_STATEMENT);
            TreeUtil.addChildren(element, expr);
            processClosingSemicolon(element, lexer);
            return element;
          }
          lexer.restore(pos);
        }

        TreeElement decl = myContext.getDeclarationParsing().parseDeclaration(lexer,
                                                                              DeclarationParsing.Context.CODE_BLOCK_CONTEXT);
        if (decl != null) {
          CompositeElement declStatement = Factory.createCompositeElement(DECLARATION_STATEMENT);
          TreeUtil.addChildren(declStatement, decl);
          return declStatement;
        }

        if (lexer.getTokenType() == IDENTIFIER) {
          TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
          lexer.advance();
          if (lexer.getTokenType() != COLON) {
            lexer.restore(pos);
          }
          else {
            CompositeElement element = Factory.createCompositeElement(LABELED_STATEMENT);
            TreeUtil.addChildren(element, identifier);
            TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
            TreeElement statement = parseStatement(lexer);
            if (statement != null) {
              TreeUtil.addChildren(element, statement);
            }
            return element;
          }
        }

        if (expr != null) {
          lexer.restore(pos1);
          CompositeElement element = Factory.createCompositeElement(EXPRESSION_STATEMENT);
          TreeUtil.addChildren(element, expr);
          processClosingSemicolon(element, lexer);
          return element;
        }
        else {
          return null;
        }
      }
    }
  }

  private static void skipQualifiedName(Lexer lexer) {
    if (lexer.getTokenType() != IDENTIFIER) return;
    while(true){
      lexer.advance();
      if (lexer.getTokenType() != DOT) return;
      final LexerPosition position = lexer.getCurrentPosition();
      lexer.advance();
      if (lexer.getTokenType() != IDENTIFIER){
        lexer.restore(position);
        return;
      }
    }
  }

  private CompositeElement parseIfStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == IF_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(IF_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement thenStatement = parseStatement(lexer);
    if (thenStatement == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      return element;
    }

    TreeUtil.addChildren(element, thenStatement);

    if (lexer.getTokenType() != ELSE_KEYWORD){
      return element;
    }

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement elseStatement = parseStatement(lexer);
    if (elseStatement == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      return element;
    }
    TreeUtil.addChildren(element, elseStatement);
    return element;
  }

  private CompositeElement parseWhileStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == WHILE_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(WHILE_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      return element;
    }

    TreeUtil.addChildren(element, statement);
    return element;
  }

  private CompositeElement parseForStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == FOR_KEYWORD);
    final TreeElement forKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    if (lexer.getTokenType() != LPARENTH){
      CompositeElement errorForElement = Factory.createCompositeElement(FOR_STATEMENT);
      TreeUtil.addChildren(errorForElement, forKeyword);
      TreeUtil.addChildren(errorForElement, Factory.createErrorElement("'(' expected"));
      return errorForElement;
    }

    final TreeElement lparenth = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    final LexerPosition afterLParenth = lexer.getCurrentPosition();
    final TreeElement parameter = myContext.getDeclarationParsing().parseParameter(lexer, false);
    if (parameter == null || parameter.getElementType() != PARAMETER || lexer.getTokenType() != COLON) {
      lexer.restore(afterLParenth);
      return parseForLoopFromInitialization(forKeyword, lparenth, lexer);
    }
    else {
      return parseForEachFromColon(forKeyword, lparenth, parameter, lexer);
    }
  }

  private CompositeElement parseForEachFromColon(TreeElement forKeyword,
                                                 TreeElement lparenth,
                                                 TreeElement parameter,
                                                 Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == COLON);
    final CompositeElement element = Factory.createCompositeElement(Factory.FOREACH_STATEMENT);
    TreeUtil.addChildren(element, forKeyword);
    TreeUtil.addChildren(element, lparenth);
    TreeUtil.addChildren(element, parameter);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    final CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null) {
      TreeUtil.addChildren(element, expr);
    }
    else {
      TreeUtil.addChildren(element, Factory.createErrorElement("Expression expected"));
    }
    if (lexer.getTokenType() == RPARENTH) {
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      final TreeElement body = parseStatement(lexer);
      if (body != null) {
        TreeUtil.addChildren(element, body);
      } else {
        TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      }
    }
    else {
      TreeUtil.addChildren(element, Factory.createErrorElement("')' expected"));
    }
    return element;
  }

  private CompositeElement parseForLoopFromInitialization(final TreeElement forKeyword,
                                                          final TreeElement lparenth,
                                                          Lexer lexer) {// parsing normal for statement for statement
    CompositeElement element = Factory.createCompositeElement(FOR_STATEMENT);
    TreeUtil.addChildren(element, forKeyword);
    TreeUtil.addChildren(element, lparenth);
    TreeElement init = parseStatement(lexer);
    if (init == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      if (lexer.getTokenType() != RPARENTH){
        return element;
      }
    }
    else{
      TreeUtil.addChildren(element, init);

      CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr != null){
        TreeUtil.addChildren(element, expr);
      }

      if (lexer.getTokenType() != SEMICOLON){
        TreeUtil.addChildren(element, Factory.createErrorElement("';' expected"));
        if (lexer.getTokenType() != RPARENTH){
          return element;
        }
      }
      else{
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        parseExpressionOrExpressionList(lexer, element);
        if (lexer.getTokenType() != RPARENTH){
          TreeUtil.addChildren(element, Factory.createErrorElement("')' expected"));
          return element;
        }
      }
    }

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      return element;
    }

    TreeUtil.addChildren(element, statement);

    return element;
  }

  private void parseExpressionOrExpressionList(Lexer lexer, CompositeElement element) {
    CompositeElement expression = myContext.getExpressionParsing().parseExpression(lexer);
    if (expression != null) {
      final CompositeElement expressionStatement;
      if (lexer.getTokenType() != COMMA) {
        expressionStatement = Factory.createCompositeElement(EXPRESSION_STATEMENT);
        TreeUtil.addChildren(expressionStatement, expression);
      } else {
        expressionStatement = Factory.createCompositeElement(EXPRESSION_LIST_STATEMENT);
        final CompositeElement expressionList = Factory.createCompositeElement(EXPRESSION_LIST);
        TreeUtil.addChildren(expressionList, expression);
        do {
          TreeUtil.addChildren(expressionList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          final CompositeElement nextExpression = myContext.getExpressionParsing().parseExpression(lexer);
          if (nextExpression != null) {
            TreeUtil.addChildren(expressionList, nextExpression);
          } else {
            TreeUtil.addChildren(expressionList,  Factory.createErrorElement("Expression expected"));
          }
        } while (lexer.getTokenType() == COMMA);
        TreeUtil.addChildren(expressionStatement, expressionList);
      }
      TreeUtil.addChildren(element, expressionStatement);
    }
  }

  private CompositeElement parseDoWhileStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == DO_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(DO_WHILE_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement statement = parseStatement(lexer);
    if (statement == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Statement expected"));
      return element;
    }

    TreeUtil.addChildren(element, statement);

    if (lexer.getTokenType() != WHILE_KEYWORD){
      TreeUtil.addChildren(element, Factory.createErrorElement("'while' expected"));
      return element;
    }

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    processClosingSemicolon(element, lexer);

    return element;
  }

  private CompositeElement parseSwitchStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == SWITCH_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(SWITCH_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("'{' expected"));
      return element;
    }

    TreeUtil.addChildren(element, codeBlock);
    return element;
  }

  private CompositeElement parseSwitchLabelStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == CASE_KEYWORD || lexer.getTokenType() == DEFAULT_KEYWORD);
    IElementType tokenType = lexer.getTokenType();
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement element = Factory.createCompositeElement(SWITCH_LABEL_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    if (tokenType == CASE_KEYWORD){
      TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr == null){
        lexer.restore(pos);
        return null;
      }
      TreeUtil.addChildren(element, expr);
    }
    if (lexer.getTokenType() == COLON){
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      TreeUtil.addChildren(element, Factory.createErrorElement("':' expected"));
    }
    return element;
  }

  private CompositeElement parseBreakStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == BREAK_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(BREAK_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() == IDENTIFIER){
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseContinueStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == CONTINUE_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(CONTINUE_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() == IDENTIFIER){
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseReturnStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == RETURN_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(RETURN_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null){
      TreeUtil.addChildren(element, expr);
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseThrowStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == THROW_KEYWORD);

    CompositeElement element = Factory.createCompositeElement(THROW_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr != null){
      TreeUtil.addChildren(element, expr);
    }
    else{
      TreeUtil.addChildren(element, Factory.createErrorElement("Expression expected"));
      return element;
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseSynchronizedStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == SYNCHRONIZED_KEYWORD);
    CompositeElement element = Factory.createCompositeElement(SYNCHRONIZED_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (!processExpressionInParens(lexer, element)){
      return element;
    }

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("'{' expected"));
      return element;
    }

    TreeUtil.addChildren(element, codeBlock);
    return element;
  }

  private CompositeElement parseTryStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == TRY_KEYWORD);

    //int pos = ParseUtil.savePosition(lexer);
    CompositeElement element = Factory.createCompositeElement(TRY_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("'{' expected"));
      return element;
    }
    TreeUtil.addChildren(element, codeBlock);

    if (lexer.getTokenType() != CATCH_KEYWORD && lexer.getTokenType() != FINALLY_KEYWORD){
      TreeUtil.addChildren(element, Factory.createErrorElement("'catch' or 'finally' expected"));
      return element;
    }

    while(lexer.getTokenType() == CATCH_KEYWORD){
      CompositeElement catchSection = parseCatchSection(lexer);
      TreeUtil.addChildren(element, catchSection);
      if (catchSection.getLastChildNode().getElementType() == ERROR_ELEMENT) break;
    }

    if (lexer.getTokenType() == FINALLY_KEYWORD){
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
      if (codeBlock == null){
        TreeUtil.addChildren(element, Factory.createErrorElement("'{' expected"));
        return element;
      }
      TreeUtil.addChildren(element, codeBlock);
    }

    return element;
  }

  private CompositeElement parseCatchSection(Lexer lexer) {
    TreeElement codeBlock;
    CompositeElement catchSection = Factory.createCompositeElement(CATCH_SECTION);
    TreeUtil.addChildren(catchSection, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() != LPARENTH) {
      TreeUtil.addChildren(catchSection, Factory.createErrorElement("'(' expected"));
      return catchSection;
    }

    TreeUtil.addChildren(catchSection, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement parm = myContext.getDeclarationParsing().parseParameter(lexer, false);
    if (parm == null) {
      TreeUtil.addChildren(catchSection, Factory.createErrorElement("Parameter expected"));
      return catchSection;
    }
    TreeUtil.addChildren(catchSection, parm);

    if (lexer.getTokenType() != RPARENTH) {
      TreeUtil.addChildren(catchSection, Factory.createErrorElement("')' expected"));
      return catchSection;
    }

    TreeUtil.addChildren(catchSection, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    if (codeBlock == null) {
      TreeUtil.addChildren(catchSection, Factory.createErrorElement("'{' expected"));
      return catchSection;
    }
    TreeUtil.addChildren(catchSection, codeBlock);
    return catchSection;
  }

  private CompositeElement parseAssertStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == ASSERT_KEYWORD);

    CompositeElement element = Factory.createCompositeElement(ASSERT_STATEMENT);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr == null){
      TreeUtil.addChildren(element, Factory.createErrorElement("Boolean expression expected"));
      return element;
    }

    TreeUtil.addChildren(element, expr);

    if (lexer.getTokenType() == COLON){
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      TreeElement expr2 = myContext.getExpressionParsing().parseExpression(lexer);
      if (expr2 == null){
        TreeUtil.addChildren(element, Factory.createErrorElement("Expression expected"));
        return element;
      }

      TreeUtil.addChildren(element, expr2);
    }

    processClosingSemicolon(element, lexer);
    return element;
  }

  private CompositeElement parseBlockStatement(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == LBRACE);
    CompositeElement element = Factory.createCompositeElement(BLOCK_STATEMENT);
    TreeElement codeBlock = parseCodeBlock(lexer, DEEP_PARSE_BLOCKS_IN_STATEMENTS);
    TreeUtil.addChildren(element, codeBlock);
    return element;
  }

  private boolean processExpressionInParens(Lexer lexer, CompositeElement parent) {
    if (lexer.getTokenType() != LPARENTH){
      TreeUtil.addChildren(parent, Factory.createErrorElement("'(' expected"));
      return false;
    }

    TreeUtil.addChildren(parent, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    final LexerPosition beforeExprPos = lexer.getCurrentPosition();
    CompositeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
    if (expr == null || lexer.getTokenType() == SEMICOLON){
      if (expr != null){
        lexer.restore(beforeExprPos);
      }
      TreeUtil.addChildren(parent, Factory.createErrorElement("Expression expected"));
      if (lexer.getTokenType() != RPARENTH){
        return false;
      }
    }
    else{
      TreeUtil.addChildren(parent, expr);

      if (lexer.getTokenType() != RPARENTH){
        TreeUtil.addChildren(parent, Factory.createErrorElement("')' expected"));
        return false;
      }
    }

    // add ')'
    TreeUtil.addChildren(parent, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    return true;
  }

  private void processClosingSemicolon(CompositeElement statement, Lexer lexer) {
    if (lexer.getTokenType() == SEMICOLON){
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      TreeUtil.addChildren(statement, Factory.createErrorElement("';' expected"));
    }
  }

  public TreeElement parseCatchSectionText(char[] buffer) {
    Lexer lexer = new JavaLexer(myContext.getLanguageLevel());
    final FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, 0, buffer.length);

    CompositeElement catchSection = parseCatchSection(filterLexer);
    if (catchSection == null) return null;
    if (filterLexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(catchSection, lexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return catchSection;
  }
}
