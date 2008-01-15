package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTFactory;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

/**
 *  @author dsl
 */
class GTTokens implements JavaTokenType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.GTTokens");
  static IElementType getTokenType(Lexer lexer) {
    if (lexer.getTokenType() != GT) return lexer.getTokenType();
    final LexerPosition originalPosition = lexer.getCurrentPosition();
    final int prevTokenEnd = lexer.getTokenEnd();
    lexer.advance();
    if (lexer.getTokenStart() != prevTokenEnd) {
      lexer.restore(originalPosition);
      return GT;
    }
    final IElementType resultType;
    IElementType i1 = lexer.getTokenType();
    if (i1 == EQ) {
      resultType = GE;
    }
    else if (i1 == GT) {
      {
        final int prevTokenEnd2 = lexer.getTokenEnd();
        lexer.advance();
        if (lexer.getTokenStart() != prevTokenEnd2) {
          resultType = GTGT;
        }
        else {
          IElementType i = lexer.getTokenType();
          if (i == GT) {
            final int prevTokenEnd3 = lexer.getTokenEnd();
            lexer.advance();
            if (lexer.getTokenStart() != prevTokenEnd3 || lexer.getTokenType() != EQ) {
              resultType = GTGTGT;
            }
            else {
              resultType = GTGTGTEQ;
            }
          }
          else if (i == EQ) {
            resultType = GTGTEQ;
          }
          else {
            resultType = GTGT;
          }
        }
      }
    }
    else {
      resultType = GT;
    }
    lexer.restore(originalPosition);
    return resultType;
  }

  static TreeElement createTokenElementAndAdvance(IElementType tokenType, Lexer lexer, CharTable table) {
    final TreeElement result;
    if (tokenType == GTGT || tokenType == GE) {
      result = mergeTokens(1, lexer, tokenType, table);
    }
    else if (tokenType == GTGTEQ || tokenType == GTGTGT) {
      result = mergeTokens(2, lexer, tokenType, table);
    }
    else if (tokenType == GTGTGTEQ) {
      result = mergeTokens(3, lexer, tokenType, table);
    }
    else {
      LOG.assertTrue(tokenType == lexer.getTokenType());
      result = ParseUtil.createTokenElement(lexer, table);
    }
    lexer.advance();
    return result;
  }

  static void advance(IElementType tokenType, Lexer lexer) {
    if(lexer.getTokenType() != GT){
      lexer.advance();
    }
    else {
      if (tokenType == GTGTGTEQ) {
        lexer.advance();
        lexer.advance();
        lexer.advance();
      }
      else if (tokenType == GTGTEQ || tokenType == GTGTGT) {
        lexer.advance();
        lexer.advance();
      }
      else if (tokenType == GTGT || tokenType == GE) {
        lexer.advance();
      }
      lexer.advance();
    }
  }

  private static TreeElement mergeTokens(int nTokens, Lexer lexer, IElementType tokenType, CharTable table) {
    final int tokenStart = lexer.getTokenStart();
    for (int i = 0; i < nTokens; i++) {
      lexer.advance();
    }
    final int tokenEnd = lexer.getTokenEnd();
    return ASTFactory.leaf(tokenType, lexer.getBufferSequence(), tokenStart, tokenEnd, table);
  }
}
