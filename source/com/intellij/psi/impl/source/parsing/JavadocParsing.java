package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaDocLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;

public class JavadocParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.JavadocParsing");

  private final static TokenSet TOKEN_FILTER = TokenSet.create(DOC_SPACE,
                                                               DOC_COMMENT_LEADING_ASTERISKS);

  private final static TokenSet TAG_VALUE = TokenSet.create(DOC_TAG_VALUE_TOKEN,
                                                            DOC_TAG_VALUE_COMMA,
                                                            DOC_TAG_VALUE_DOT,
                                                            DOC_TAG_VALUE_LPAREN,
                                                            DOC_TAG_VALUE_RPAREN,
                                                            DOC_TAG_VALUE_SHARP_TOKEN,
                                                            DOC_TAG_VALUE_LT,
                                                            DOC_TAG_VALUE_GT);

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

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    final CompositeElement element;

    if (isType){
      element = parseTypeWithEllipsis(lexer, true, false);
    }
    else{
      element = myContext.getStatementParsing().parseJavaCodeReference(lexer, true, true);
    }

    if (element != null){
      dummyRoot.rawAddChildren(element);
    }
    while(lexer.getTokenType() != null){
      dummyRoot.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, myBuffer.length(), 0, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.getFirstChildNode();
  }

  public TreeElement parseDocCommentText(PsiManager manager, CharSequence buffer, int startOffset, int endOffset) {
    Lexer originalLexer = new JavaDocLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel().hasEnumKeywordAndAutoboxing()); // we need caching lexer because the lexer has states

    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(TOKEN_FILTER));
    lexer.start(buffer, startOffset, endOffset, 0);
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (tokenType == DOC_TAG_NAME) {
        CompositeElement tag = parseTag(manager, lexer);
        dummyRoot.rawAddChildren(tag);
      }
      else {
        TreeElement element = parseDataItem(manager, lexer, null, false);
        dummyRoot.rawAddChildren(element);
      }
    }

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, startOffset, endOffset, -1, new JDTokenProcessor(this), myContext);
    return dummyRoot.getFirstChildNode();
  }

  private CompositeElement parseTag(PsiManager manager, Lexer lexer) {
    if (lexer.getTokenType() != DOC_TAG_NAME) return null;
    CompositeElement tag = ASTFactory.composite(DOC_TAG);
    tag.rawAddChildren(createTokenElement(lexer));
    String tagName = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    lexer.advance();
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null || tokenType == DOC_TAG_NAME || tokenType == DOC_COMMENT_END) break;
      TreeElement element = parseDataItem(manager, lexer, tagName, false);
      tag.rawAddChildren(element);
    }
    return tag;
  }

  private TreeElement parseDataItem(PsiManager manager, Lexer lexer, String tagName, boolean isInlineItem) {
    if (lexer.getTokenType() == DOC_INLINE_TAG_START) {
      LeafElement justABrace = ASTFactory.leaf(DOC_COMMENT_DATA, myContext.tokenText(lexer));
      CompositeElement tag = ASTFactory.composite(DOC_INLINE_TAG);
      final LeafElement leafElement = ASTFactory.leaf(DOC_INLINE_TAG_START, myContext.tokenText(lexer));
      tag.rawAddChildren(leafElement);

      lexer.advance();

      if (myBraceScope > 0) {
        myBraceScope++;
        return justABrace;
      }

      if (lexer.getTokenType() != DOC_TAG_NAME &&
          lexer.getTokenType() != DOC_COMMENT_BAD_CHARACTER) {
        return justABrace;
      }

      myBraceScope++;

      String inlineTagName = "";

      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == DOC_TAG_NAME) {
          inlineTagName = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
        }

        if (tokenType == null || tokenType == DOC_COMMENT_END) break;
        TreeElement element = parseDataItem(manager, lexer, inlineTagName, true);
        tag.rawAddChildren(element);
        if (tokenType == DOC_INLINE_TAG_END) {
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
        if (LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel().compareTo(LanguageLevel.JDK_1_4) >= 0 &&
                 LINKPLAIN_TAG.equals(tagName) && isInlineItem) {
          return parseSeeTagValue(lexer);
        }
        else if (!isInlineItem && (THROWS_TAG.equals(tagName) || EXCEPTION_TAG.equals(tagName))) {
          final TreeElement element = parseReferenceOrType(lexer, false);
          lexer.advance();
          final CompositeElement tagValue = ASTFactory.composite(DOC_TAG_VALUE_TOKEN);
          tagValue.rawAddChildren(element);
          return tagValue;
        }
        else if (!isInlineItem && tagName != null && tagName.equals(PARAM_TAG)) {
          return parseParamTagValue(lexer);
        }
        else {
          if (LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0 && VALUE_TAG.equals(tagName) && isInlineItem) {
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
    CompositeElement tagValue = ASTFactory.composite(DOC_PARAMETER_REF);

    while (TAG_VALUE.contains(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      tagValue.rawAddChildren(value);
    }

    return tagValue;
  }

  private TreeElement parseSimpleTagValue(Lexer lexer) {
    CompositeElement tagValue = ASTFactory.composite(DOC_TAG_VALUE_TOKEN);

    while (TAG_VALUE.contains(lexer.getTokenType())) {
      TreeElement value = createTokenElement(lexer);
      lexer.advance();
      tagValue.rawAddChildren(value);
    }

    return tagValue;
  }

  private ASTNode parseMethodRef(Lexer lexer) {
    CompositeElement ref = ASTFactory.composite(DOC_METHOD_OR_FIELD_REF);

    TreeElement sharp = createTokenElement(lexer);
    ref.rawAddChildren(sharp);
    lexer.advance();

    if (lexer.getTokenType() != DOC_TAG_VALUE_TOKEN) return ref;
    TreeElement value = createTokenElement(lexer);
    ref.rawAddChildren(value);
    lexer.advance();

    if (lexer.getTokenType() == DOC_TAG_VALUE_LPAREN) {
      TreeElement lparen = createTokenElement(lexer);
      lexer.advance();
      ref.rawAddChildren(lparen);

      CompositeElement subValue = ASTFactory.composite(DOC_TAG_VALUE_TOKEN);
      ref.rawAddChildren(subValue);

      while (TAG_VALUE.contains(lexer.getTokenType())) {
        if (lexer.getTokenType() == DOC_TAG_VALUE_TOKEN) {
          final TreeElement reference = parseReferenceOrType(lexer, true);
          lexer.advance();
          subValue.rawAddChildren(reference);

          while (TAG_VALUE.contains(lexer.getTokenType()) && lexer.getTokenType() != DOC_TAG_VALUE_COMMA &&
                 lexer.getTokenType() != DOC_TAG_VALUE_RPAREN) {
            final TreeElement tokenElement = createTokenElement(lexer);
            lexer.advance();
            subValue.rawAddChildren(tokenElement);
          }
        }
        else if (lexer.getTokenType() == DOC_TAG_VALUE_RPAREN) {
          TreeElement rparen = createTokenElement(lexer);
          lexer.advance();
          ref.rawAddChildren(rparen);
          return ref;
        }
        else {
          final TreeElement tokenElement = createTokenElement(lexer);
          lexer.advance();
          subValue.rawAddChildren(tokenElement);
        }
      }
    }

    return ref;
  }

  private TreeElement parseSeeTagValue(Lexer lexer) {
    if (!TAG_VALUE.contains(lexer.getTokenType())) return null;

    if (lexer.getTokenType() == DOC_TAG_VALUE_SHARP_TOKEN) {
      return (TreeElement)parseMethodRef(lexer);
    }
    else if (lexer.getTokenType() == DOC_TAG_VALUE_TOKEN) {
      final TreeElement element = parseReferenceOrType(lexer, false);
      lexer.advance();

      if (lexer.getTokenType() == DOC_TAG_VALUE_SHARP_TOKEN) {
        ASTNode methodRef = parseMethodRef(lexer);
        ((TreeElement)methodRef.getFirstChildNode()).rawInsertBeforeMe(element);
        return (TreeElement)methodRef;
      }
      else {
        return element;
      }
    }
    else {
      CompositeElement tagValue = ASTFactory.composite(DOC_TAG_VALUE_TOKEN);
      TreeElement element = createTokenElement(lexer);
      lexer.advance();
      tagValue.rawAddChildren(element);
      return tagValue;
    }
  }

  private TreeElement parseReferenceOrType(Lexer lexer, boolean isType) {
    return ASTFactory.lazy(isType ? DOC_TYPE_HOLDER : DOC_REFERENCE_HOLDER, myContext.tokenText(lexer));
  }

  private LeafElement createTokenElement(Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == DOC_SPACE) {
      tokenType = WHITE_SPACE;
    }
    else if ((tokenType == DOC_INLINE_TAG_START || tokenType == DOC_INLINE_TAG_END) && myBraceScope != 1) {
      tokenType = DOC_COMMENT_DATA;
    }

    return ASTFactory.leaf(tokenType, myContext.tokenText(lexer));
  }

  private static class JDTokenProcessor implements TokenProcessor {
    private final JavadocParsing myParsing;

    private JDTokenProcessor(JavadocParsing theParsing) {
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
