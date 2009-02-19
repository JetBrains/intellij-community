package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ExpressionParsing");

  public ExpressionParsing(JavaParsingContext context) {
    super(context);
  }

  public static CompositeElement parseExpressionText(PsiManager manager, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel();
    Lexer originalLexer = new JavaLexer(level);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset, 0);
    JavaParsingContext context = new JavaParsingContext(table, level);
    CompositeElement expression = context.getExpressionParsing().parseExpression(lexer);
    if (expression == null) return null;
    expression.putUserData(CharTable.CHAR_TABLE_KEY, table);
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    dummyRoot.rawAddChildren(expression);
    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return expression;
  }

  public TreeElement parseExpressionText(final Lexer originalLexer,
                                         final CharSequence buffer,
                                         final int startOffset,
                                         final int endOffset,
                                         PsiManager manager) {
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset,0);
    CharTable table = myContext.getCharTable();
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    CompositeElement expression = parseExpression(lexer);
    if (expression != null)
      dummyRoot.rawAddChildren(expression);

    CompositeElement parent = dummyRoot;
    final IElementType tokenType = lexer.getTokenType();

    if(tokenType != null && tokenType != TokenType.BAD_CHARACTER){
      final CompositeElement errorElement = Factory.createErrorElement("Unexpected tokens");
      dummyRoot.rawAddChildren(errorElement);
      parent = errorElement;
    }

    while(lexer.getTokenType() != null){
      parent.rawAddChildren(ParseUtil.createTokenElement(lexer, table));
      lexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.getFirstChildNode();
  }

  public TreeElement parseExpressionTextFragment(PsiManager manager, CharSequence buffer, int startOffset, int endOffset, int state) {
    Lexer originalLexer = new JavaLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state >= 0) {
      lexer.start(buffer, startOffset, endOffset, state);
    }
    else {
      lexer.start(buffer, startOffset, endOffset, 0);
    }

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement expression = parseExpression(lexer);
    if (expression != null) {
      dummyRoot.rawAddChildren(expression);
    }

    if (lexer.getTokenType() != null) {
      dummyRoot.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("unexpected.tokens.beyond.the.end.of.expression")));
      while (lexer.getTokenType() != null) {
        dummyRoot.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), state, WhiteSpaceAndCommentsProcessor.INSTANCE,
                                  myContext);
    return dummyRoot.getFirstChildNode();
  }

  public CompositeElement parseExpression(Lexer lexer) {
    return parseConstruct(lexer, PARSE_ASSIGNMENT);
  }

  private static final int PARSE_ASSIGNMENT = 0;
  private static final int PARSE_CONDITIONAL = 1;
  private static final int PARSE_COND_OR = 2;
  private static final int PARSE_COND_AND = 3;
  private static final int PARSE_OR = 4;
  private static final int PARSE_XOR = 5;
  private static final int PARSE_AND = 6;
  private static final int PARSE_EQUALITY = 7;
  private static final int PARSE_RELATIONAL = 8;
  private static final int PARSE_SHIFT = 9;
  private static final int PARSE_ADDITIVE = 10;
  private static final int PARSE_MULTIPLICATIVE = 11;
  private static final int PARSE_UNARY = 12;
  private static final int PARSE_POSTFIX = 13;
  private static final int PARSE_PRIMARY = 14;
  private static final int PARSE_CLASS_OBJECT_ACCESS = 15;
  private static final int PARSE_ARGUMENT_LIST = 16;
  private static final int PARSE_ARRAY_INITIALIZER = 17;
  private static final int PARSE_TYPE = 18;

  private static final TokenSet COND_OR_SIGN_BIT_SET = TokenSet.create(OROR);
  private static final TokenSet COND_AND_SIGN_BIT_SET = TokenSet.create(ANDAND);
  private static final TokenSet OR_SIGN_BIT_SET = TokenSet.create(OR);
  private static final TokenSet XOR_SIGN_BIT_SET = TokenSet.create(XOR);
  private static final TokenSet AND_SIGN_BIT_SET = TokenSet.create(AND);
  private static final TokenSet EQUALITY_SIGN_BIT_SET = TokenSet.create(EQEQ, NE);
  private static final TokenSet SHIFT_SIGN_BIT_SET = TokenSet.create(LTLT, GTGT, GTGTGT);
  private static final TokenSet ADDITIVE_SIGN_BIT_SET = TokenSet.create(PLUS, MINUS);
  private static final TokenSet MULTIPLICATIVE_SIGN_BIT_SET = TokenSet.create(ASTERISK, DIV, PERC);

  private CompositeElement parseConstruct(Lexer lexer, int number) {
    switch (number) {
      case PARSE_ASSIGNMENT:
        return parseAssignmentExpression(lexer);

      case PARSE_CONDITIONAL:
        return parseConditionalExpression(lexer);

      case PARSE_COND_OR:
        return parseBinaryExpression(lexer, PARSE_COND_AND, COND_OR_SIGN_BIT_SET);

      case PARSE_COND_AND:
        return parseBinaryExpression(lexer, PARSE_OR, COND_AND_SIGN_BIT_SET);

      case PARSE_OR:
        return parseBinaryExpression(lexer, PARSE_XOR, OR_SIGN_BIT_SET);

      case PARSE_XOR:
        return parseBinaryExpression(lexer, PARSE_AND, XOR_SIGN_BIT_SET);

      case PARSE_AND:
        return parseBinaryExpression(lexer, PARSE_EQUALITY, AND_SIGN_BIT_SET);

      case PARSE_EQUALITY:
        return parseBinaryExpression(lexer, PARSE_RELATIONAL, EQUALITY_SIGN_BIT_SET);

      case PARSE_RELATIONAL:
        return parseRelationalExpression(lexer);

      case PARSE_SHIFT:
        return parseBinaryExpression(lexer, PARSE_ADDITIVE, SHIFT_SIGN_BIT_SET);

      case PARSE_ADDITIVE:
        return parseBinaryExpression(lexer, PARSE_MULTIPLICATIVE, ADDITIVE_SIGN_BIT_SET);

      case PARSE_MULTIPLICATIVE:
        return parseBinaryExpression(lexer, PARSE_UNARY, MULTIPLICATIVE_SIGN_BIT_SET);

      case PARSE_UNARY:
        return parseUnaryExpression(lexer);

      case PARSE_POSTFIX:
        return parsePostfixExpression(lexer);

      case PARSE_PRIMARY:
        return parsePrimaryExpression(lexer);

      case PARSE_CLASS_OBJECT_ACCESS:
        return parseClassObjectAccessExpression(lexer);

      case PARSE_ARGUMENT_LIST:
        return parseArgumentList(lexer);

      case PARSE_ARRAY_INITIALIZER:
        return parseArrayInitializerExpression(lexer);

      case PARSE_TYPE:
        return parseType(lexer);

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  private CompositeElement parseBinaryExpression(Lexer lexer, int argNumber, TokenSet opSignBitSet) {
    CompositeElement element = parseConstruct(lexer, argNumber);
    if (element == null) return null;

    while (true) {
      IElementType tokenType = GTTokens.getTokenType(lexer);
      if (tokenType == null) break;
      if (!opSignBitSet.contains(tokenType)) break;

      CompositeElement result = ASTFactory.composite(BINARY_EXPRESSION);
      result.rawAddChildren(element);
      result.rawAddChildren(GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

      TreeElement element1 = parseConstruct(lexer, argNumber);
      if (element1 == null) {
        result.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        return result;
      }

      result.rawAddChildren(element1);

      element = result;
    }

    return element;
  }

  private CompositeElement parseAssignmentExpression(Lexer lexer) {
    CompositeElement element1 = parseConditionalExpression(lexer);
    if (element1 == null) return null;

    IElementType tokenType = GTTokens.getTokenType(lexer);
    if (tokenType == EQ ||
        tokenType == ASTERISKEQ ||
        tokenType == DIVEQ ||
        tokenType == PERCEQ ||
        tokenType == PLUSEQ ||
        tokenType == MINUSEQ ||
        tokenType == LTLTEQ ||
        tokenType == GTGTEQ ||
        tokenType == GTGTGTEQ ||
        tokenType == ANDEQ ||
        tokenType == OREQ ||
        tokenType == XOREQ) {
      CompositeElement element = ASTFactory.composite(ASSIGNMENT_EXPRESSION);
      element.rawAddChildren(element1);

      element.rawAddChildren(GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

      TreeElement element2 = parseAssignmentExpression(lexer);
      if (element2 == null) {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        return element;
      }

      element.rawAddChildren(element2);
      return element;
    }
    else {
      return element1;
    }
  }

  public CompositeElement parseConditionalExpression(Lexer lexer) {
    CompositeElement element1 = parseConstruct(lexer, PARSE_COND_OR);
    if (element1 == null) return null;

    if (lexer.getTokenType() != QUEST) return element1;

    CompositeElement element = ASTFactory.composite(CONDITIONAL_EXPRESSION);
    element.rawAddChildren(element1);

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement element2 = parseExpression(lexer);
    if (element2 == null) {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      return element;
    }

    element.rawAddChildren(element2);

    if (lexer.getTokenType() != COLON) {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.colon")));
      return element;
    }

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement element3 = parseConditionalExpression(lexer);
    if (element3 == null) {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      return element;
    }

    element.rawAddChildren(element3);

    return element;
  }

  private CompositeElement parseRelationalExpression(Lexer lexer) {
    CompositeElement element = parseConstruct(lexer, PARSE_SHIFT);
    if (element == null) return null;

    while (true) {
      IElementType tokenType = GTTokens.getTokenType(lexer);
      if (tokenType == null) break;

      IElementType elementType;
      int argNumber;
      if (tokenType == LT || tokenType == GT || tokenType == LE || tokenType == GE) {
        elementType = BINARY_EXPRESSION;
        argNumber = PARSE_SHIFT;
      }
      else if (tokenType == INSTANCEOF_KEYWORD) {
        elementType = INSTANCE_OF_EXPRESSION;
        argNumber = PARSE_TYPE;
      }
      else {
        break;
      }

      CompositeElement result = ASTFactory.composite(elementType);
      result.rawAddChildren(element);
      result.rawAddChildren(GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

      TreeElement element1 = parseConstruct(lexer, argNumber);
      if (element1 == null) {
        CompositeElement errorElement = Factory.createErrorElement(argNumber == PARSE_TYPE ?
                                                                   JavaErrorMessages.message("expected.type") :
                                                                   JavaErrorMessages.message("expected.expression"));
        result.rawAddChildren(errorElement);
        return result;
      }

      result.rawAddChildren(element1);

      element = result;
    }

    return element;
  }

  private CompositeElement parseUnaryExpression(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == PLUS || tokenType == MINUS || tokenType == PLUSPLUS || tokenType == MINUSMINUS || tokenType == TILDE || tokenType == EXCL) {
      CompositeElement element = ASTFactory.composite(PREFIX_EXPRESSION);
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      CompositeElement element1 = parseUnaryExpression(lexer);
      if (element1 == null) {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        return element;
      }
      element.rawAddChildren(element1);
      return element;
    }
    else if (tokenType == LPARENTH) {
      final LexerPosition pos = lexer.getCurrentPosition();

      TreeElement lparenth = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      TreeElement type = parseType(lexer);
      if (type == null || lexer.getTokenType() != RPARENTH) {
        lexer.restore(pos);
        return parsePostfixExpression(lexer);
      }

      final TreeElement rparenth = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      if (lexer.getTokenType() == PLUS || lexer.getTokenType() == MINUS ||
          lexer.getTokenType() == PLUSPLUS || lexer.getTokenType() == MINUSMINUS) {
        if (!PRIMITIVE_TYPE_BIT_SET.contains(type.getFirstChildNode().getElementType())) {
          lexer.restore(pos);
          return parsePostfixExpression(lexer);
        }
      }

      final CompositeElement expr = parseUnaryExpression(lexer);
      if (expr == null) {
        final TreeElement lastNode = TreeUtil.findLastLeaf(type);
        if (lastNode.getElementType() != JavaTokenType.GT) { //cannot parse correct parenthesized expression if we already parsed correct parameterized type
          lexer.restore(pos);
          return parsePostfixExpression(lexer);
        }
      }

      CompositeElement element = ASTFactory.composite(TYPE_CAST_EXPRESSION);
      element.rawAddChildren(lparenth);
      element.rawAddChildren(type);
      element.rawAddChildren(rparenth);
      if (expr != null) {
        element.rawAddChildren(expr);
      } else {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      }
      return element;
    }
    else {
      return parsePostfixExpression(lexer);
    }
  }

  private CompositeElement parsePostfixExpression(Lexer lexer) {
    CompositeElement element = parsePrimaryExpression(lexer);
    if (element == null) return null;

    while (lexer.getTokenType() == PLUSPLUS || lexer.getTokenType() == MINUSMINUS) {
      CompositeElement element1 = ASTFactory.composite(POSTFIX_EXPRESSION);
      element1.rawAddChildren(element);
      element1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      element = element1;
    }

    return element;
  }

  private CompositeElement parsePrimaryExpression(Lexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();

    CompositeElement element = parsePrimaryExpressionStart(lexer);
    if (element == null) return null;

    while (true) {
      IElementType i = lexer.getTokenType();
      if (i == DOT) {
        final LexerPosition pos = lexer.getCurrentPosition();
        TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();

        IElementType tokenType = lexer.getTokenType();
        if (tokenType == LT) {
          final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
          CompositeElement element1 = ASTFactory.composite(REFERENCE_EXPRESSION);
          element1.rawAddChildren(element);
          element1.rawAddChildren(dot);

          element1.rawAddChildren(referenceParameterList);
          if (lexer.getTokenType() == IDENTIFIER) {
            element1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
          }
          else {
            element1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
            return element1;
          }

          element = element1;
          //}
        }
        else if (tokenType == CLASS_KEYWORD && element.getElementType() == REFERENCE_EXPRESSION) {
          final LexerPosition pos1 = lexer.getCurrentPosition();
          lexer.restore(startPos);
          CompositeElement element1 = parseClassObjectAccessExpression(lexer);
          if (lexer.getTokenStart() <= pos1.getOffset()) {
            lexer.restore(pos1);
            element.rawAddChildren(dot);
            element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
            return element;
          }
          element = element1;
        }
        else if (tokenType == NEW_KEYWORD) {
          //final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
          element = parseNewExpression(lexer, element, dot/*, referenceParameterList*/);
        }
        else if ((tokenType == THIS_KEYWORD || tokenType == SUPER_KEYWORD)
                 && element.getElementType() == REFERENCE_EXPRESSION) {

          lexer.restore(startPos);
          CompositeElement element1 = parseJavaCodeReference(lexer, false, true); // don't eat the last dot before "this" or "super"!
          if (element1 == null || lexer.getTokenType() != DOT || lexer.getTokenStart() != pos.getOffset()) {
            lexer.restore(pos);
            return element;
          }

          IElementType type = tokenType == THIS_KEYWORD ? THIS_EXPRESSION : SUPER_EXPRESSION;
          CompositeElement element2 = ASTFactory.composite(type);
          element2.rawAddChildren(element1);
          element2.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          if (lexer.getTokenType() != tokenType) {
            lexer.restore(pos);
            return element;
          }
          element2.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          element = element2;
        }
        else if (tokenType == SUPER_KEYWORD) {
          CompositeElement element1 = ASTFactory.composite(REFERENCE_EXPRESSION);
          element1.rawAddChildren(element);
          element1.rawAddChildren(dot);
          element1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          element = element1;
        }
        else {
          final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
          CompositeElement element1 = ASTFactory.composite(REFERENCE_EXPRESSION);
          element1.rawAddChildren(element);
          element1.rawAddChildren(dot);

          element1.rawAddChildren(referenceParameterList);
          if (lexer.getTokenType() == IDENTIFIER) {
            element1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
          }
          else {
            element1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
            return element1;
          }

          element = element1;
        }
      }
      else if (i == LPARENTH) {
        if (element.getElementType() != REFERENCE_EXPRESSION) {
          if (element.getElementType() == SUPER_EXPRESSION) {
            //convert to REFERENCE_EXPRESSION
            final LexerPosition pos = lexer.getCurrentPosition();
            lexer.restore(startPos);
            CompositeElement qualifier = parsePrimaryExpressionStart(lexer);
            if (qualifier != null) {
              CompositeElement element1 = ASTFactory.composite(REFERENCE_EXPRESSION);
              element1.rawAddChildren(qualifier);
              if (lexer.getTokenType() == DOT) {
                TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
                lexer.advance();
                element1.rawAddChildren(dot);
                if (lexer.getTokenType() == SUPER_KEYWORD) {
                  TreeElement superKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
                  lexer.advance();
                  element1.rawAddChildren(superKeyword);
                  element = element1;
                  continue;
                }
              }
            }

            lexer.restore(pos);
            return element;
          } else return element;
        }
        CompositeElement element1 = ASTFactory.composite(METHOD_CALL_EXPRESSION);
        element1.rawAddChildren(element);
        TreeElement argumentList = parseArgumentList(lexer);
        element1.rawAddChildren(argumentList);
        element = element1;
      }
      else if (i == LBRACKET) {
        final LexerPosition pos = lexer.getCurrentPosition();
        TreeElement lbracket = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();
        if (lexer.getTokenType() == RBRACKET && element.getElementType() == REFERENCE_EXPRESSION) {
          lexer.restore(startPos);
          CompositeElement element1 = parseClassObjectAccessExpression(lexer);
          if (lexer.getTokenStart() <= pos.getOffset()) {
            lexer.restore(pos);
            return element;
          }
          element = element1;
        }
        else {
          CompositeElement element1 = ASTFactory.composite(ARRAY_ACCESS_EXPRESSION);
          element1.rawAddChildren(element);
          element1.rawAddChildren(lbracket);

          TreeElement expr = parseExpression(lexer);
          if (expr == null) {
            element1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
            return element1;
          }

          element1.rawAddChildren(expr);

          if (lexer.getTokenType() != RBRACKET) {
            element1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
            return element1;
          }

          element1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();

          element = element1;
        }
      }
      else {
        return element;
      }
    }
  }

  private CompositeElement parsePrimaryExpressionStart(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == TRUE_KEYWORD ||
        tokenType == FALSE_KEYWORD ||
        tokenType == NULL_KEYWORD ||
        tokenType == INTEGER_LITERAL ||
        tokenType == LONG_LITERAL ||
        tokenType == FLOAT_LITERAL ||
        tokenType == DOUBLE_LITERAL ||
        tokenType == CHARACTER_LITERAL ||
        tokenType == STRING_LITERAL) {
      CompositeElement element = ASTFactory.composite(LITERAL_EXPRESSION);
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return element;
    }
    else if (tokenType == LPARENTH) {
      CompositeElement element = ASTFactory.composite(PARENTH_EXPRESSION);
      //int pos = ParseUtil.savePosition(lexer);
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      TreeElement expression = parseExpression(lexer);
      if (expression == null) {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      }
      else {
        element.rawAddChildren(expression);
      }

      if (lexer.getTokenType() != RPARENTH) {
        if (expression != null) {
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
        }
      }
      else {
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      return element;
    }
    else if (tokenType == LBRACE) {
      return parseArrayInitializerExpression(lexer);
    }
    else if (tokenType == IDENTIFIER) {
      CompositeElement element = ASTFactory.composite(REFERENCE_EXPRESSION);
      element.rawAddChildren(ASTFactory.composite(REFERENCE_PARAMETER_LIST));
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return element;
    }
    else if (tokenType == THIS_KEYWORD) {
      TreeElement thisKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      CompositeElement element;
      if (lexer.getTokenType() != LPARENTH) {
        element = ASTFactory.composite(THIS_EXPRESSION);
      }
      else {
        element = ASTFactory.composite(REFERENCE_EXPRESSION);
      }

      element.rawAddChildren(ASTFactory.composite(REFERENCE_PARAMETER_LIST));
      element.rawAddChildren(thisKeyword);

      return element;
    }
    else if (tokenType == SUPER_KEYWORD) {
      TreeElement superKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      CompositeElement element;
      if (lexer.getTokenType() != LPARENTH) {
        element = ASTFactory.composite(SUPER_EXPRESSION);
      }
      else {
        element = ASTFactory.composite(REFERENCE_EXPRESSION);
      }

      element.rawAddChildren(ASTFactory.composite(REFERENCE_PARAMETER_LIST));
      element.rawAddChildren(superKeyword);
      return element;
    }
    else if (tokenType == NEW_KEYWORD) {//final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
      return parseNewExpression(lexer, null, null/*, referenceParameterList*/);
    }
    else {
      if (tokenType != null && PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
        return parseClassObjectAccessExpression(lexer);
      }
      return null;
    }
  }

  private CompositeElement parseNewExpression(Lexer lexer,
                                              TreeElement qualifier,
                                              TreeElement dot/*, TreeElement referenceParameterList*/) {
    LOG.assertTrue(lexer.getTokenType() == NEW_KEYWORD);

    CompositeElement element = ASTFactory.composite(NEW_EXPRESSION);

    if (qualifier != null) {
      element.rawAddChildren(qualifier);
      element.rawAddChildren(dot);
    }

    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    element.rawAddChildren(parseReferenceParameterList(lexer, false));

    boolean isPrimitive;
    TreeElement refOrType;
    if (lexer.getTokenType() == IDENTIFIER) {
      isPrimitive = false;
      refOrType = parseJavaCodeReference(lexer, true, true);
    }
    else if (lexer.getTokenType() != null && PRIMITIVE_TYPE_BIT_SET.contains(lexer.getTokenType())) {
      isPrimitive = true;
      refOrType = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
    }
    else {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return element;
    }

    if (!isPrimitive && lexer.getTokenType() == LPARENTH) {
      TreeElement argumentList = parseArgumentList(lexer);
      if (lexer.getTokenType() == LBRACE && refOrType.getElementType() == JAVA_CODE_REFERENCE) { // anonymous class
        CompositeElement classElement = ASTFactory.composite(ANONYMOUS_CLASS);
        element.rawAddChildren(classElement);
        classElement.rawAddChildren(refOrType);
        classElement.rawAddChildren(argumentList);
        myContext.getDeclarationParsing().parseClassBodyWithBraces(classElement, lexer, false, false);
      }
      else {
        element.rawAddChildren(refOrType);
        element.rawAddChildren(argumentList);
      }
    }
    else {
      element.rawAddChildren(refOrType);

      if (lexer.getTokenType() != LBRACKET) {
        String description = isPrimitive ?
                             JavaErrorMessages.message("expected.lbracket") :
                             JavaErrorMessages.message("expected.lparen.or.lbracket");
        element.rawAddChildren(Factory.createErrorElement(description));
        return element;
      }

      int bracketCount = 0;
      int dimCount = 0;
      while (true) {
        if (lexer.getTokenType() != LBRACKET) break;

        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        if (bracketCount == dimCount) {
          TreeElement dimExpr = parseExpression(lexer);
          if (dimExpr != null) {
            element.rawAddChildren(dimExpr);
            dimCount++;
          }
        }
        bracketCount++;

        if (lexer.getTokenType() != RBRACKET) {
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          return element;
        }

        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      if (dimCount == 0) {
        if (lexer.getTokenType() == LBRACE) {
          TreeElement initializer = parseArrayInitializerExpression(lexer);
          if (initializer != null) {
            element.rawAddChildren(initializer);
          }
        }
        else {
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.array.initializer")));
        }
      }
    }

    return element;
  }

  private CompositeElement parseClassObjectAccessExpression(Lexer lexer) {
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement type = parseType(lexer, false, false); // don't eat last dot before "class"!
    if (type == null) return null;
    if (lexer.getTokenType() != DOT) {
      lexer.restore(pos);
      return null;
    }
    TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();
    if (lexer.getTokenType() != CLASS_KEYWORD) {
      lexer.restore(pos);
      return null;
    }
    CompositeElement element = ASTFactory.composite(CLASS_OBJECT_ACCESS_EXPRESSION);
    element.rawAddChildren(type);
    element.rawAddChildren(dot);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    return element;
  }

  public CompositeElement parseArgumentList(Lexer lexer) {
    if (lexer.getTokenType() != LPARENTH) {
      // [dsl,ven] used in EnumConstant
      return ASTFactory.composite(EXPRESSION_LIST);
    }
    LOG.assertTrue(lexer.getTokenType() == LPARENTH);

    CompositeElement element = ASTFactory.composite(EXPRESSION_LIST);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    boolean first = true;
    while (true) {
      if (first && (lexer.getTokenType() == RPARENTH || lexer.getTokenType() == RBRACE || lexer.getTokenType() == RBRACKET)) break;
      if (!first && isArgListFinished(lexer)) break;

      boolean errored = false;
      if (!first) {
        if (lexer.getTokenType() == COMMA) {
          element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }
        else {
          errored = true;
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.comma.or.rparen")));
          element.rawAddChildren(ASTFactory.composite(EMPTY_EXPRESSION));
        }
      }
      first = false;

      TreeElement arg = parseExpression(lexer);
      if (arg == null) {
        if (!errored) {
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          element.rawAddChildren(ASTFactory.composite(EMPTY_EXPRESSION));
        }
        if (isArgListFinished(lexer)) break;

        if (lexer.getTokenType() != COMMA) {
          element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }
      } else {
        element.rawAddChildren(arg);
      }
    }

    if (lexer.getTokenType() == RPARENTH) {
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else {
      element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
      element.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
    }

    return element;
  }

  public static boolean isArgListFinished(final Lexer lexer) {
    final IElementType type = lexer.getTokenType();
    return type != IDENTIFIER && type != BAD_CHARACTER && type != COMMA && type != INTEGER_LITERAL && type != STRING_LITERAL;
  }

  private CompositeElement parseArrayInitializerExpression(Lexer lexer) {
    if (lexer.getTokenType() != LBRACE) return null;

    CompositeElement element = ASTFactory.composite(ARRAY_INITIALIZER_EXPRESSION);
    element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    boolean firstExpressionMissing = false;
    while (true) {
      if (lexer.getTokenType() == RBRACE) {
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }

      if (lexer.getTokenType() == null) {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
        element.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
        return element;
      }

      if (firstExpressionMissing) {
        // before comma must be an expression 
        element.getLastChildNode().rawInsertBeforeMe(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      }

      TreeElement arg = parseExpression(lexer);
      if (arg == null) {
        if (lexer.getTokenType() == COMMA) {
          firstExpressionMissing = true;
        }
        else {
          element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
          element.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
          return element;
        }
      }
      else {
        element.rawAddChildren(arg);
      }

      if (lexer.getTokenType() == RBRACE) {
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }

      if (lexer.getTokenType() == COMMA) {
        element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else {
        element.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.comma")));
      }
    }
  }
}



