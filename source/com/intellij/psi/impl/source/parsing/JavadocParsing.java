package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaDocLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.JavaProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;

public class JavadocParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.JavadocParsing");

  private final static TokenSet TOKEN_FILTER = TokenSet.create(JavaDocTokenType.DOC_SPACE,
                                                               JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS);

  private final static TokenSet TAG_VALUE = TokenSet.create(JavaDocTokenType.DOC_TAG_VALUE_TOKEN,
                                                            JavaDocTokenType.DOC_TAG_VALUE_COMMA,
                                                            JavaDocTokenType.DOC_TAG_VALUE_DOT,
                                                            JavaDocTokenType.DOC_TAG_VALUE_LPAREN,
                                                            JavaDocTokenType.DOC_TAG_VALUE_RPAREN,
                                                            JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN,
                                                            JavaDocTokenType.DOC_TAG_VALUE_LT,
                                                            JavaDocTokenType.DOC_TAG_VALUE_GT);

  private int myBraceScope = 0;
  @NonNls private static final String SEE_TAG = "@see";
  @NonNls private static final String LINK_TAG = "@link";
  @NonNls private static final String LINKPLAIN_TAG = "@linkplain";
  @NonNls private static final String THROWS_TAG = "@throws";
  @NonNls private static final String EXCEPTION_TAG = "@exception";
  @NonNls private static final String PARAM_TAG = "@param";
  @NonNls private static final String VALUE_TAG = "@value";

  public JavadocParsing(JavaParsingContext context) {
    super(context);
  }

  public TreeElement parseJavaDocReference(CharSequence myBuffer, Lexer originalLexer, boolean isType, PsiManager manager) {
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(myBuffer, 0, myBuffer.length(), 0);

    final FileElement dummyRoot = new JavaDummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    final CompositeElement element;

    if (isType){
      element = parseTypeWithEllipsis(lexer, true, false);
    }
    else{
      element = myContext.getStatementParsing().parseJavaCodeReference(lexer, true, true);
    }

    if (element != null){
      TreeUtil.addChildren(dummyRoot, element);
    }
    while(lexer.getTokenType() != null){
      TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, myBuffer.length(), 0, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.getFirstChildNode();
  }

  public TreeElement parseDocCommentText(PsiManager manager, CharSequence buffer, int startOffset, int endOffset) {
    Lexer originalLexer = new JavaDocLexer(JavaProjectExtension.getInstance(manager.getProject()).getLanguageLevel().hasEnumKeywordAndAutoboxing()); // we need caching lexer because the lexer has states

    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(TOKEN_FILTER));
    lexer.start(buffer, startOffset, endOffset, 0);
    final FileElement dummyRoot = new JavaDummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

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

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, startOffset, endOffset, -1, new TokenProcessor(this), myContext);
    return dummyRoot.getFirstChildNode();
  }

  private CompositeElement parseTag(PsiManager manager, Lexer lexer) {
    if (lexer.getTokenType() != JavaDocTokenType.DOC_TAG_NAME) return null;
    CompositeElement tag = Factory.createCompositeElement(DOC_TAG);
    TreeUtil.addChildren(tag, createTokenElement(lexer));
    String tagName = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
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
                                                         lexer.getBufferSequence(),
                                                         lexer.getTokenStart(),
                                                         lexer.getTokenEnd(), myContext.getCharTable());
      CompositeElement tag = Factory.createCompositeElement(DOC_INLINE_TAG);
      final LeafElement leafElement = Factory.createLeafElement(JavaDocTokenType.DOC_INLINE_TAG_START, lexer.getBufferSequence(),
                                                                lexer.getTokenStart(), lexer.getTokenEnd(), myContext.getCharTable());
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
          inlineTagName = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
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
    else if (TAG_VALUE.contains(lexer.getTokenType())) {
      if (SEE_TAG.equals(tagName) && !isInlineItem) {
        return parseSeeTagValue(lexer);
      }
      else if (LINK_TAG.equals(tagName) && isInlineItem) {
        return parseSeeTagValue(lexer);
      }
      else {
        if (JavaProjectExtension.getInstance(manager.getProject()).getLanguageLevel().compareTo(LanguageLevel.JDK_1_4) >= 0 &&
                 LINKPLAIN_TAG.equals(tagName) && isInlineItem) {
          return parseSeeTagValue(lexer);
        }
        else if (!isInlineItem && (THROWS_TAG.equals(tagName) || EXCEPTION_TAG.equals(tagName))) {
          final LeafElement element = parseReferenceOrType(lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), false);
          lexer.advance();
          final CompositeElement tagValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);
          TreeUtil.addChildren(tagValue, element);
          return tagValue;
        }
        else if (!isInlineItem && tagName != null && tagName.equals(PARAM_TAG)) {
          return parseParamTagValue(lexer);
        }
        else {
          if (JavaProjectExtension.getInstance(manager.getProject()).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0 && VALUE_TAG.equals(tagName) && isInlineItem) {
            return parseSeeTagValue(lexer);
          }
          else {
            return parseSimpleTagValue(lexer);
          }
        }
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

    while (TAG_VALUE.contains(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(tagValue, value);
    }

    return tagValue;
  }

  private TreeElement parseSimpleTagValue(Lexer lexer) {
    CompositeElement tagValue = Factory.createCompositeElement(DOC_TAG_VALUE_TOKEN);

    while (TAG_VALUE.contains(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      TreeUtil.addChildren(tagValue, value);
    }

    return tagValue;
  }

  private ASTNode parseMethodRef(Lexer lexer) {
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

      while (TAG_VALUE.contains(lexer.getTokenType())) {
        if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          final LeafElement reference = parseReferenceOrType(lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), true);
          lexer.advance();
          TreeUtil.addChildren(subValue, reference);

          while (TAG_VALUE.contains(lexer.getTokenType()) && lexer.getTokenType() != JavaDocTokenType.DOC_TAG_VALUE_COMMA &&
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
    if (!TAG_VALUE.contains(lexer.getTokenType())) return null;

    if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      return (TreeElement)parseMethodRef(lexer);
    }
    else if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      final LeafElement element = parseReferenceOrType(lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), false);
      lexer.advance();

      if (lexer.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        ASTNode methodRef = parseMethodRef(lexer);
        TreeUtil.insertBefore((TreeElement)methodRef.getFirstChildNode(), element);
        return (TreeElement)methodRef;
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

  private LeafElement parseReferenceOrType(CharSequence buffer, int startOffset, int endOffset, boolean isType) {
    return Factory.createLeafElement(isType ? JavaDocElementType.DOC_TYPE_HOLDER : JavaDocElementType.DOC_REFERENCE_HOLDER, buffer, startOffset,
                                     endOffset, myContext.getCharTable());
  }

  private LeafElement createTokenElement(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == JavaDocTokenType.DOC_SPACE) {
      tokenType = WHITE_SPACE;
    }
    else if ((tokenType == JavaDocTokenType.DOC_INLINE_TAG_START || tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) && myBraceScope != 1) {
      tokenType = JavaDocTokenType.DOC_COMMENT_DATA;
    }

    return Factory.createLeafElement(tokenType, lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), myContext.getCharTable());
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
        if (!TOKEN_FILTER.contains(type)) {
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
      return tokenType != null && TOKEN_FILTER.contains(tokenType);
    }
  }
}