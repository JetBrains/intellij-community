package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaDocLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ex.JdkUtil;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class JavadocParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.JavadocParsing");

  private final static TokenSet TOKEN_FILTER = TokenSet.create(new IElementType[]{
    JavaDocTokenType.DOC_SPACE,
    JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS,
  });

  private final static TokenSet TAG_VALUE = TokenSet.create(new IElementType[]{
    JavaDocTokenType.DOC_TAG_VALUE_TOKEN,
    JavaDocTokenType.DOC_TAG_VALUE_COMMA,
    JavaDocTokenType.DOC_TAG_VALUE_DOT,
    JavaDocTokenType.DOC_TAG_VALUE_LPAREN,
    JavaDocTokenType.DOC_TAG_VALUE_RPAREN,
    JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN,
  });

  private int myBraceScope = 0;

  public JavadocParsing(ParsingContext context) {
    super(context);
  }

  public TreeElement parseDocCommentText(PsiManager manager, char[] buffer, int startOffset, int endOffset) {
    Lexer originalLexer = new JavaDocLexer(); // we need caching lexer because the lexer has states

    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(TOKEN_FILTER));
    lexer.start(buffer, startOffset, endOffset);
    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
        CompositeElement tag = parseTag(manager, lexer);
        TreeUtil.addChildren(dummyRoot, tag);
      }
      else {
        TreeElement element = parseDataItem(manager, lexer, null, false);
        TreeUtil.addChildren(dummyRoot, element);
      }
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, startOffset, endOffset, new TokenProcessor(this), myContext);
    return dummyRoot.firstChild;
  }

  private CompositeElement parseTag(PsiManager manager, Lexer lexer) {
    if (lexer.getTokenType() != JavaDocTokenType.DOC_TAG_NAME) return null;
    CompositeElement tag = Factory.createCompositeElement(DOC_TAG);
    TreeUtil.addChildren(tag, createTokenElement(lexer));
    String tagName = new String(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd() - lexer.getTokenStart());
    lexer.advance();
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_TAG_NAME || tokenType == JavaDocTokenType.DOC_COMMENT_END) break;
      TreeElement element = parseDataItem(manager, lexer, tagName, false);
      TreeUtil.addChildren(tag, element);
    }
    return tag;
  }

  private TreeElement parseDataItem(PsiManager manager, Lexer lexer, String tagName, boolean isInlineItem) {
    if (lexer.getTokenType() == JavaDocTokenType.DOC_INLINE_TAG_START) {
      LeafElement justABrace = Factory.createLeafElement(JavaDocTokenType.DOC_COMMENT_DATA,
                                                         lexer.getBuffer(),
                                                         lexer.getTokenStart(),
                                                         lexer.getTokenEnd(), lexer.getState(), myContext.getCharTable());
      justABrace.setState(lexer.getState());
      CompositeElement tag = Factory.createCompositeElement(DOC_INLINE_TAG);
      final LeafElement leafElement = Factory.createLeafElement(JavaDocTokenType.DOC_INLINE_TAG_START, lexer.getBuffer(),
                                                                lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState(),
                                                                myContext.getCharTable());
      leafElement.setState(lexer.getState());
      TreeUtil.addChildren(tag, leafElement);

      lexer.advance();

      if (myBraceScope > 0) {
        myBraceScope++;
        return justABrace;
      }

      if (lexer.getTokenType() != JavaDocTokenType.DOC_TAG_NAME &&
          lexer.getTokenType() != JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
        return justABrace;
      }

      myBraceScope++;

      String inlineTagName = "";

      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
          inlineTagName = new String(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd() - lexer.getTokenStart());
        }

        if (tokenType == null || tokenType == JavaDocTokenType.DOC_COMMENT_END) break;
        TreeElement element = parseDataItem(manager, lexer, inlineTagName, true);
        TreeUtil.addChildren(tag, element);
        if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
          if (myBraceScope > 0) myBraceScope--;
          if (myBraceScope == 0) break;
        }
      }
      return tag;
    }
    else if (TAG_VALUE.isInSet(lexer.getTokenType())) {
      if (tagName != null && tagName.equals("@see") && !isInlineItem) {
        return parseSeeTagValue(lexer);
      }
      else if (tagName != null && tagName.equals("@link") && isInlineItem) {
        return parseSeeTagValue(lexer);
      }
      else if (tagName != null && JdkUtil.isJdk14(manager.getProject()) && tagName.equals("@linkplain") && isInlineItem) {
        return parseSeeTagValue(lexer);
      }
      else if (tagName != null && !isInlineItem && (tagName.equals("@throws") || tagName.equals("@exception"))) {
        final LeafElement element = parseReferenceOrType(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(), false,
                                                         lexer.getState());
        element.setState(lexer.getState());
        lexer.advance();
        final CompositeElement tagValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);
        TreeUtil.addChildren(tagValue, element);
        return tagValue;
      }
      else if (!isInlineItem && tagName != null && tagName.equals("@param")) {
        return parseParamTagValue(lexer);
      }
      else {
        return parseSimpleTagValue(lexer);
      }
    }
    else {
      TreeElement token = createTokenElement(lexer);
      lexer.advance();
      return token;
    }
  }

  private TreeElement parseParamTagValue(Lexer lexer) {
    CompositeElement tagValue = Factory.createCompositeElement(DOC_PARAMETER_REF);

    while (TAG_VALUE.isInSet(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(tagValue, value);
    }

    return tagValue;
  }

  private TreeElement parseSimpleTagValue(Lexer lexer) {
    CompositeElement tagValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);

    while (TAG_VALUE.isInSet(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(tagValue, value);
    }

    return tagValue;
  }

  private CompositeElement parseMethodRef(Lexer lexer) {
    CompositeElement ref = Factory.createCompositeElement(DOC_METHOD_OR_FIELD_REF);

    TreeElement sharp = createTokenElement(lexer);
    TreeUtil.addChildren(ref, sharp);
    lexer.advance();

    if (lexer.getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) return ref;
    TreeElement value = createTokenElement(lexer);
    TreeUtil.addChildren(ref, value);
    lexer.advance();

    if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
      TreeElement lparen = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(ref, lparen);

      CompositeElement subValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);
      TreeUtil.addChildren(ref, subValue);

      while (TAG_VALUE.isInSet(lexer.getTokenType())) {
        if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          final LeafElement reference = parseReferenceOrType(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(), true,
                                                             lexer.getState());
          reference.setState(lexer.getState());
          lexer.advance();
          TreeUtil.addChildren(subValue, reference);

          while (TAG_VALUE.isInSet(lexer.getTokenType()) && lexer.getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_COMMA &&
                 lexer.getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
            final TreeElement tokenElement = createTokenElement(lexer);
            lexer.advance();
            TreeUtil.addChildren(subValue, tokenElement);
          }
        }
        else if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
          TreeElement rparen = createTokenElement(lexer);
          lexer.advance();
          TreeUtil.addChildren(ref, rparen);
          return ref;
        }
        else {
          final TreeElement tokenElement = createTokenElement(lexer);
          lexer.advance();
          TreeUtil.addChildren(subValue, tokenElement);
        }
      }
    }

    return ref;
  }

  private TreeElement parseSeeTagValue(Lexer lexer) {
    if (!TAG_VALUE.isInSet(lexer.getTokenType())) return null;

    if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      return parseMethodRef(lexer);
    }
    else if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      final LeafElement element = parseReferenceOrType(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(), false,
                                                       lexer.getState());
      element.setState(lexer.getState());
      lexer.advance();

      if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        CompositeElement methodRef = parseMethodRef(lexer);
        TreeUtil.insertBefore(methodRef.firstChild, element);
        return methodRef;
      }
      else {
        return element;
      }
    }
    else {
      CompositeElement tagValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);
      TreeElement element = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(tagValue, element);
      return tagValue;
    }
  }

  private LeafElement parseReferenceOrType(char[] buffer, int startOffset, int endOffset, boolean isType, int lexerState) {
    return Factory.createLeafElement(isType ? JavaDocTokenType.DOC_TYPE_TEXT : JavaDocTokenType.DOC_REFERENCE_TEXT, buffer, startOffset,
                                     endOffset, lexerState, myContext.getCharTable());
  }

  private LeafElement createTokenElement(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == JavaDocTokenType.DOC_SPACE) {
      tokenType = WHITE_SPACE;
    }
    else if ((tokenType == JavaDocTokenType.DOC_INLINE_TAG_START || tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) && myBraceScope != 1) {
      tokenType = JavaDocTokenType.DOC_COMMENT_DATA;
    }

    final LeafElement leafElement = Factory.createLeafElement(tokenType, lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(),
                                                              lexer.getState(), myContext.getCharTable());
    leafElement.setState(lexer.getState());
    return leafElement;
  }

  private static class TokenProcessor implements ParseUtil.TokenProcessor {
    private JavadocParsing myParsing;

    private TokenProcessor(JavadocParsing theParsing) {
      myParsing = theParsing;
    }

    public TreeElement process(Lexer lexer, ParsingContext context) {
      TreeElement first = null;
      TreeElement last = null;
      while (isTokenValid(lexer.getTokenType())) {
        LeafElement tokenElement = myParsing.createTokenElement(lexer);
        IElementType type = lexer.getTokenType();
        if (!TOKEN_FILTER.isInSet(type)) {
          LOG.assertTrue(false, "Missed token should be space or asterisks:" + tokenElement);
          throw new RuntimeException();
        }
        if (last != null) {
          last.setTreeNext(tokenElement);
          tokenElement.setTreePrev(last);
          last = tokenElement;
        }
        else {
          first = last = tokenElement;
        }
        lexer.advance();
      }
      return first;
    }

    public boolean isTokenValid(IElementType tokenType) {
      return tokenType != null && TOKEN_FILTER.isInSet(tokenType);
    }
  }
}