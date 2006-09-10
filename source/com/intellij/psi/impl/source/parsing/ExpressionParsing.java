package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ExpressionParsing");

  public ExpressionParsing(JavaParsingContext context) {
    super(context);
  }

  public static CompositeElement parseExpressionText(PsiManager manager, char[] buffer, int startOffset, int endOffset, CharTable table) {
    final LanguageLevel level = manager.getEffectiveLanguageLevel();
    Lexer originalLexer = new JavaLexer(level);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);
    JavaParsingContext context = new JavaParsingContext(table, level);
    CompositeElement expression = context.getExpressionParsing().parseExpression(lexer);
    if (expression == null) return null;
    expression.putUserData(CharTable.CHAR_TABLE_KEY, table);
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(expression, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    final FileElement dummyRoot = new DummyHolder(manager, null, table).getTreeElement();
    TreeUtil.addChildren(dummyRoot, expression);
    return expression;
  }

  public TreeElement parseExpressionText(final Lexer originalLexer,
                                         final char[] buffer,
                                         final int startOffset,
                                         final int endOffset,
                                         PsiManager manager) {
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);
    CharTable table = myContext.getCharTable();
    final FileElement dummyRoot = new DummyHolder(manager, null, table).getTreeElement();
    CompositeElement expression = parseExpression(lexer);
    if (expression != null)
      TreeUtil.addChildren(dummyRoot, expression);

    CompositeElement parent = dummyRoot;
    final IElementType tokenType = lexer.getTokenType();

    if(tokenType != null && tokenType != ElementType.BAD_CHARACTER){
      final CompositeElement errorElement = Factory.createErrorElement("Unexpected tokens");
      TreeUtil.addChildren(dummyRoot, errorElement);
      parent = errorElement;
    }

    while(lexer.getTokenType() != null){
      TreeUtil.addChildren(parent, ParseUtil.createTokenElement(lexer, table));
      lexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.getFirstChildNode();
  }

  public TreeElement parseExpressionTextFragment(PsiManager manager, char[] buffer, int startOffset, int endOffset, int state) {
    Lexer originalLexer = new JavaLexer(manager.getEffectiveLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state >= 0) {
      lexer.start(buffer, startOffset, endOffset, state);
    }
    else {
      lexer.start(buffer, startOffset, endOffset);
    }

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement expression = parseExpression(lexer);
    if (expression != null) {
      TreeUtil.addChildren(dummyRoot, expression);
    }

    if (lexer.getTokenType() != null) {
      TreeUtil.addChildren(dummyRoot, Factory.createErrorElement(JavaErrorMessages.message("unexpected.tokens.beyond.the.end.of.expression")));
      while (lexer.getTokenType() != null) {
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length, state, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE,
                                  myContext);
    return dummyRoot.getFirstChildNode();
  }

  public CompositeElement parseExpression(Lexer lexer) {
    return parseConstruct((FilterLexer)lexer, PARSE_ASSIGNMENT);
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

  private static final TokenSet COND_OR_SIGN_BIT_SET = TokenSet.create(new IElementType[]{OROR});
  private static final TokenSet COND_AND_SIGN_BIT_SET = TokenSet.create(new IElementType[]{ANDAND});
  private static final TokenSet OR_SIGN_BIT_SET = TokenSet.create(new IElementType[]{OR});
  private static final TokenSet XOR_SIGN_BIT_SET = TokenSet.create(new IElementType[]{XOR});
  private static final TokenSet AND_SIGN_BIT_SET = TokenSet.create(new IElementType[]{AND});
  private static final TokenSet EQUALITY_SIGN_BIT_SET = TokenSet.create(new IElementType[]{EQEQ, NE});
  private static final TokenSet SHIFT_SIGN_BIT_SET = TokenSet.create(new IElementType[]{LTLT, GTGT, GTGTGT});
  private static final TokenSet ADDITIVE_SIGN_BIT_SET = TokenSet.create(new IElementType[]{PLUS, MINUS});
  private static final TokenSet MULTIPLICATIVE_SIGN_BIT_SET = TokenSet.create(new IElementType[]{ASTERISK, DIV, PERC});

  private CompositeElement parseConstruct(FilterLexer lexer, int number) {
    switch (number) {
      case PARSE_ASSIGNMENT:
        return parseAssignmentExpression(lexer);

      case PARSE_CONDITIONAL:
        return parseConditionalExpression(lexer);

      case PARSE_COND_OR:
        return parseBinaryExpression(lexer, PARSE_COND_AND, COND_OR_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_COND_AND:
        return parseBinaryExpression(lexer, PARSE_OR, COND_AND_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_OR:
        return parseBinaryExpression(lexer, PARSE_XOR, OR_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_XOR:
        return parseBinaryExpression(lexer, PARSE_AND, XOR_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_AND:
        return parseBinaryExpression(lexer, PARSE_EQUALITY, AND_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_EQUALITY:
        return parseBinaryExpression(lexer, PARSE_RELATIONAL, EQUALITY_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_RELATIONAL:
        return parseRelationalExpression(lexer);

      case PARSE_SHIFT:
        return parseBinaryExpression(lexer, PARSE_ADDITIVE, SHIFT_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_ADDITIVE:
        return parseBinaryExpression(lexer, PARSE_MULTIPLICATIVE, ADDITIVE_SIGN_BIT_SET, BINARY_EXPRESSION);

      case PARSE_MULTIPLICATIVE:
        return parseBinaryExpression(lexer, PARSE_UNARY, MULTIPLICATIVE_SIGN_BIT_SET, BINARY_EXPRESSION);

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

  private CompositeElement parseBinaryExpression(FilterLexer lexer, int argNumber, TokenSet opSignBitSet, IElementType elementType) {
    CompositeElement element = parseConstruct(lexer, argNumber);
    if (element == null) return null;

    while (true) {
      IElementType tokenType = GTTokens.getTokenType(lexer);
      if (tokenType == null) break;
      if (!opSignBitSet.contains(tokenType)) break;

      CompositeElement result = Factory.createCompositeElement(elementType);
      TreeUtil.addChildren(result, element);
      TreeUtil.addChildren(result, GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

      TreeElement element1 = parseConstruct(lexer, argNumber);
      if (element1 == null) {
        TreeUtil.addChildren(result, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        return result;
      }

      TreeUtil.addChildren(result, element1);

      element = result;
    }

    return element;
  }

  private CompositeElement parseAssignmentExpression(FilterLexer lexer) {
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
      {
        CompositeElement element = Factory.createCompositeElement(ASSIGNMENT_EXPRESSION);
        TreeUtil.addChildren(element, element1);

        TreeUtil.addChildren(element, GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

        TreeElement element2 = parseAssignmentExpression(lexer);
        if (element2 == null) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          return element;
        }

        TreeUtil.addChildren(element, element2);
        return element;
      }
    }
    else {
      return element1;
    }
  }

  public CompositeElement parseConditionalExpression(Lexer lexer) {
    CompositeElement element1 = parseConstruct((FilterLexer)lexer, PARSE_COND_OR);
    if (element1 == null) return null;

    if (lexer.getTokenType() != QUEST) return element1;

    CompositeElement element = Factory.createCompositeElement(CONDITIONAL_EXPRESSION);
    TreeUtil.addChildren(element, element1);

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement element2 = parseExpression(lexer);
    if (element2 == null) {
      TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      return element;
    }

    TreeUtil.addChildren(element, element2);

    if (lexer.getTokenType() != COLON) {
      TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.colon")));
      return element;
    }

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement element3 = parseConditionalExpression(lexer);
    if (element3 == null) {
      TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
      return element;
    }

    TreeUtil.addChildren(element, element3);

    return element;
  }

  private CompositeElement parseRelationalExpression(FilterLexer lexer) {
    CompositeElement element = parseConstruct(lexer, PARSE_SHIFT);
    if (element == null) return null;

    Loop:
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
        break Loop;
      }

      CompositeElement result = Factory.createCompositeElement(elementType);
      TreeUtil.addChildren(result, element);
      TreeUtil.addChildren(result, GTTokens.createTokenElementAndAdvance(tokenType, lexer, myContext.getCharTable()));

      TreeElement element1 = parseConstruct(lexer, argNumber);
      if (element1 == null) {
        CompositeElement errorElement = Factory.createErrorElement(argNumber == PARSE_TYPE ?
                                                                   JavaErrorMessages.message("expected.type") :
                                                                   JavaErrorMessages.message("expected.expression"));
        TreeUtil.addChildren(result, errorElement);
        return result;
      }

      TreeUtil.addChildren(result, element1);

      element = result;
    }

    return element;
  }

  private CompositeElement parseUnaryExpression(FilterLexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == PLUS || tokenType == MINUS || tokenType == PLUSPLUS || tokenType == MINUSMINUS || tokenType == TILDE || tokenType == EXCL) {
      {
        CompositeElement element = Factory.createCompositeElement(PREFIX_EXPRESSION);
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        CompositeElement element1 = parseUnaryExpression(lexer);
        if (element1 == null) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          return element;
        }
        TreeUtil.addChildren(element, element1);
        return element;
      }
    }
    else if (tokenType == LPARENTH) {
      {
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

        CompositeElement element = Factory.createCompositeElement(TYPE_CAST_EXPRESSION);
        TreeUtil.addChildren(element, lparenth);
        TreeUtil.addChildren(element, type);
        TreeUtil.addChildren(element, rparenth);
        if (expr != null) {
          TreeUtil.addChildren(element, expr);
        } else {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        }
        return element;
      }
    }
    else {
      return parsePostfixExpression(lexer);
    }
  }

  private CompositeElement parsePostfixExpression(FilterLexer lexer) {
    CompositeElement element = parsePrimaryExpression(lexer);
    if (element == null) return null;

    while (lexer.getTokenType() == PLUSPLUS || lexer.getTokenType() == MINUSMINUS) {
      CompositeElement element1 = Factory.createCompositeElement(POSTFIX_EXPRESSION);
      TreeUtil.addChildren(element1, element);
      TreeUtil.addChildren(element1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      element = element1;
    }

    return element;
  }

  private CompositeElement parsePrimaryExpression(FilterLexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();

    CompositeElement element = parsePrimaryExpressionStart(lexer);
    if (element == null) return null;

    while (true) {
      IElementType i = lexer.getTokenType();
      if (i == DOT) {
        {
          final LexerPosition pos = lexer.getCurrentPosition();
          TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
          lexer.advance();

          IElementType tokenType = lexer.getTokenType();
          if (tokenType == LT) {
            final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
            CompositeElement element1 = Factory.createCompositeElement(REFERENCE_EXPRESSION);
            TreeUtil.addChildren(element1, element);
            TreeUtil.addChildren(element1, dot);

            TreeUtil.addChildren(element1, referenceParameterList);
            if (lexer.getTokenType() == IDENTIFIER) {
              TreeUtil.addChildren(element1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
              lexer.advance();
            }
            else {
              TreeUtil.addChildren(element1, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
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
              TreeUtil.addChildren(element, dot);
              TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
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
            CompositeElement element2 = Factory.createCompositeElement(type);
            TreeUtil.addChildren(element2, element1);
            TreeUtil.addChildren(element2, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
            if (lexer.getTokenType() != tokenType) {
              lexer.restore(pos);
              return element;
            }
            TreeUtil.addChildren(element2, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
            element = element2;
          }
          else if (tokenType == SUPER_KEYWORD) {
            CompositeElement element1 = Factory.createCompositeElement(REFERENCE_EXPRESSION);
            TreeUtil.addChildren(element1, element);
            TreeUtil.addChildren(element1, dot);
            TreeUtil.addChildren(element1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();
            element = element1;
          }
          else {
            final TreeElement referenceParameterList = parseReferenceParameterList(lexer, false);
            CompositeElement element1 = Factory.createCompositeElement(REFERENCE_EXPRESSION);
            TreeUtil.addChildren(element1, element);
            TreeUtil.addChildren(element1, dot);

            TreeUtil.addChildren(element1, referenceParameterList);
            if (lexer.getTokenType() == IDENTIFIER) {
              TreeUtil.addChildren(element1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
              lexer.advance();
            }
            else {
              TreeUtil.addChildren(element1, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
              return element1;
            }

            element = element1;
          }
        }
      }
      else if (i == LPARENTH) {
        {
          if (element.getElementType() != REFERENCE_EXPRESSION) {
            if (element.getElementType() == SUPER_EXPRESSION) {
              //convert to REFERENCE_EXPRESSION
              final LexerPosition pos = lexer.getCurrentPosition();
              lexer.restore(startPos);
              CompositeElement qualifier = parsePrimaryExpressionStart(lexer);
              if (qualifier != null) {
                CompositeElement element1 = Factory.createCompositeElement(REFERENCE_EXPRESSION);
                TreeUtil.addChildren(element1, qualifier);
                if (lexer.getTokenType() == DOT) {
                  TreeElement dot = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
                  lexer.advance();
                  TreeUtil.addChildren(element1, dot);
                  if (lexer.getTokenType() == SUPER_KEYWORD) {
                    TreeElement superKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
                    lexer.advance();
                    TreeUtil.addChildren(element1, superKeyword);
                    element = element1;
                    continue;
                  }
                }
              }

              lexer.restore(pos);
              return element;
            } else return element;
          }
          CompositeElement element1 = Factory.createCompositeElement(METHOD_CALL_EXPRESSION);
          TreeUtil.addChildren(element1, element);
          TreeElement argumentList = parseArgumentList(lexer);
          TreeUtil.addChildren(element1, argumentList);
          element = element1;
        }
      }
      else if (i == LBRACKET) {
        {
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
            CompositeElement element1 = Factory.createCompositeElement(ARRAY_ACCESS_EXPRESSION);
            TreeUtil.addChildren(element1, element);
            TreeUtil.addChildren(element1, lbracket);

            TreeElement expr = parseExpression(lexer);
            if (expr == null) {
              TreeUtil.addChildren(element1, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
              return element1;
            }

            TreeUtil.addChildren(element1, expr);

            if (lexer.getTokenType() != RBRACKET) {
              TreeUtil.addChildren(element1, Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
              return element1;
            }

            TreeUtil.addChildren(element1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
            lexer.advance();

            element = element1;
          }
        }
      }
      else {
        return element;
      }
    }
  }

  private CompositeElement parsePrimaryExpressionStart(FilterLexer lexer) {
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
      {
        CompositeElement element = Factory.createCompositeElement(LITERAL_EXPRESSION);
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }
    }
    else if (tokenType == LPARENTH) {
      {
        CompositeElement element = Factory.createCompositeElement(PARENTH_EXPRESSION);
        //int pos = ParseUtil.savePosition(lexer);
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        TreeElement expression = parseExpression(lexer);
        if (expression == null) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        }
        else {
          TreeUtil.addChildren(element, expression);
        }

        if (lexer.getTokenType() != RPARENTH) {
          if (expression != null) {
            TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
          }
        }
        else {
          TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }

        return element;
      }
    }
    else if (tokenType == LBRACE) {
      return parseArrayInitializerExpression(lexer);
    }
    else if (tokenType == IDENTIFIER) {
      CompositeElement element = Factory.createCompositeElement(REFERENCE_EXPRESSION);
      TreeUtil.addChildren(element, Factory.createCompositeElement(REFERENCE_PARAMETER_LIST));
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return element;
    }
    else if (tokenType == THIS_KEYWORD) {
      {
        TreeElement thisKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();

        CompositeElement element;
        if (lexer.getTokenType() != LPARENTH) {
          element = Factory.createCompositeElement(THIS_EXPRESSION);
        }
        else {
          element = Factory.createCompositeElement(REFERENCE_EXPRESSION);
        }

        TreeUtil.addChildren(element, thisKeyword);
        return element;
      }
    }
    else if (tokenType == SUPER_KEYWORD) {
      {
        TreeElement superKeyword = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();

        CompositeElement element;
        if (lexer.getTokenType() != LPARENTH) {
          element = Factory.createCompositeElement(SUPER_EXPRESSION);
        }
        else {
          element = Factory.createCompositeElement(REFERENCE_EXPRESSION);
        }

        TreeUtil.addChildren(element, superKeyword);
        return element;
      }
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

  private CompositeElement parseNewExpression(FilterLexer lexer,
                                              TreeElement qualifier,
                                              TreeElement dot/*, TreeElement referenceParameterList*/) {
    LOG.assertTrue(lexer.getTokenType() == NEW_KEYWORD);

    CompositeElement element = Factory.createCompositeElement(NEW_EXPRESSION);

    if (qualifier != null) {
      TreeUtil.addChildren(element, qualifier);
      TreeUtil.addChildren(element, dot);
    }

    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeUtil.addChildren(element, parseReferenceParameterList(lexer, false));

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
      TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return element;
    }

    if (!isPrimitive && lexer.getTokenType() == LPARENTH) {
      TreeElement argumentList = parseArgumentList(lexer);
      if (lexer.getTokenType() == LBRACE && refOrType.getElementType() == JAVA_CODE_REFERENCE) { // anonymous class
        CompositeElement classElement = Factory.createCompositeElement(ANONYMOUS_CLASS);
        TreeUtil.addChildren(element, classElement);
        TreeUtil.addChildren(classElement, refOrType);
        TreeUtil.addChildren(classElement, argumentList);
        myContext.getDeclarationParsing().parseClassBodyWithBraces(classElement, lexer, false, false);
      }
      else {
        TreeUtil.addChildren(element, refOrType);
        TreeUtil.addChildren(element, argumentList);
      }
    }
    else {
      TreeUtil.addChildren(element, refOrType);

      if (lexer.getTokenType() != LBRACKET) {
        String description = isPrimitive ?
                             JavaErrorMessages.message("expected.lbracket") :
                             JavaErrorMessages.message("expected.lparen.or.lbracket");
        TreeUtil.addChildren(element, Factory.createErrorElement(description));
        return element;
      }

      int bracketCount = 0;
      int dimCount = 0;
      while (true) {
        if (lexer.getTokenType() != LBRACKET) break;

        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        TreeElement dimExpr;
        if (bracketCount == dimCount) {
          dimExpr = parseExpression(lexer);
          if (dimExpr != null) {
            TreeUtil.addChildren(element, dimExpr);
            dimCount++;
          }
        }
        bracketCount++;

        if (lexer.getTokenType() != RBRACKET) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          return element;
        }

        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      if (dimCount == 0) {
        if (lexer.getTokenType() == LBRACE) {
          TreeElement initializer = parseArrayInitializerExpression(lexer);
          if (initializer != null) {
            TreeUtil.addChildren(element, initializer);
          }
        }
        else {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.array.initializer")));
        }
      }
    }

    return element;
  }

  private CompositeElement parseClassObjectAccessExpression(FilterLexer lexer) {
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
    CompositeElement element = Factory.createCompositeElement(CLASS_OBJECT_ACCESS_EXPRESSION);
    TreeUtil.addChildren(element, type);
    TreeUtil.addChildren(element, dot);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    return element;
  }

  public CompositeElement parseArgumentList(Lexer lexer) {
    if (lexer.getTokenType() != LPARENTH) {
      // [dsl,ven] used in EnumConstant
      return Factory.createCompositeElement(EXPRESSION_LIST);
    }
    LOG.assertTrue(lexer.getTokenType() == LPARENTH);

    CompositeElement element = Factory.createCompositeElement(EXPRESSION_LIST);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    int argCount = 0;
    while (true) {
      TreeElement arg = parseExpression(lexer);
      if (arg == null) {
        if (lexer.getTokenType() == COMMA || (lexer.getTokenType() == RPARENTH && argCount > 0)) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          TreeUtil.addChildren(element, Factory.createCompositeElement(EMPTY_EXPRESSION));
        }
        else {
          break;
        }
      }
      else {
        TreeUtil.addChildren(element, arg);
      }
      argCount++;

      if (lexer.getTokenType() != COMMA) {
        break;
      }
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    if (lexer.getTokenType() == RPARENTH) {
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else {
      TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
      element.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
    }

    return element;
  }

  private CompositeElement parseArrayInitializerExpression(FilterLexer lexer) {
    if (lexer.getTokenType() != LBRACE) return null;

    CompositeElement element = Factory.createCompositeElement(ARRAY_INITIALIZER_EXPRESSION);
    TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    while (true) {
      if (lexer.getTokenType() == RBRACE) {
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }

      if (lexer.getTokenType() == null) {
        TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
        element.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
        return element;
      }

      TreeElement arg = parseExpression(lexer);
      if (arg == null) {
        if (lexer.getTokenType() != COMMA) {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
          element.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
          return element;
        }
        else {
          TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
        }
      }
      else {
        TreeUtil.addChildren(element, arg);
      }

      if (lexer.getTokenType() == RBRACE) {
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return element;
      }

      if (lexer.getTokenType() == COMMA) {
        TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else {
        TreeUtil.addChildren(element, Factory.createErrorElement(JavaErrorMessages.message("expected.comma")));
      }
    }
  }
}



