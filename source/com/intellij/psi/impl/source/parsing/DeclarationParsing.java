package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class DeclarationParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.DeclarationParsing");

  public static enum Context {
    FILE_CONTEXT,
    CLASS_CONTEXT,
    CODE_BLOCK_CONTEXT,
    ANNOTATION_INTERFACE_CONTEXT
  }

  public DeclarationParsing(JavaParsingContext context) {
    super(context);
  }

  public TreeElement parseEnumConstantText(CharSequence buffer) {
    Lexer originalLexer = new JavaLexer(LanguageLevel.JDK_1_5);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer);
    return parseEnumConstant(lexer);
  }

  public TreeElement parseDeclarationText(PsiManager manager,
                                          LanguageLevel languageLevel,
                                          CharSequence buffer,
                                          Context context) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer);
    TreeElement first = parseDeclaration(lexer, context);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    dummyRoot.rawAddChildren(first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  public TreeElement parseMemberValueText(PsiManager manager, CharSequence buffer, final LanguageLevel languageLevel) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer);
    TreeElement first = parseAnnotationMemberValue(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    dummyRoot.rawAddChildren(first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  protected TreeElement parseDeclaration(Lexer lexer, Context context) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == LBRACE){
      if (context == Context.FILE_CONTEXT || context == Context.CODE_BLOCK_CONTEXT) return null;
    }
    else if (tokenType == IDENTIFIER || PRIMITIVE_TYPE_BIT_SET.contains(tokenType)){
      if (context == Context.FILE_CONTEXT) return null;
    }
    else if (tokenType instanceof ILazyParseableElementType) {
      TreeElement declaration =
        ASTFactory.lazy((ILazyParseableElementType)tokenType, myContext.tokenText(lexer));
      lexer.advance();
      return declaration;
    }
    else if (!MODIFIER_BIT_SET.contains(tokenType) && !CLASS_KEYWORD_BIT_SET.contains(tokenType)
             && tokenType != AT && (context == Context.CODE_BLOCK_CONTEXT || tokenType != LT)){
      return null;
    }

    LexerPosition startPos = lexer.getCurrentPosition();

    CompositeElement modifierList = parseModifierList(lexer);

    tokenType = lexer.getTokenType();
    if (tokenType == AT) {
      TreeElement atToken = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      tokenType = lexer.getTokenType();
      if (tokenType == INTERFACE_KEYWORD) {
        return parseClassFromKeyword(lexer, modifierList, atToken);
      } else {
        lexer.restore(startPos);
        return null;
      }
    }
    else if (CLASS_KEYWORD_BIT_SET.contains(tokenType)) {
      final CompositeElement root = parseClassFromKeyword(lexer, modifierList, null);

      if (context == Context.FILE_CONTEXT) {
        boolean declsAfterEnd = false;

        while (lexer.getTokenType() != null && lexer.getTokenType() != RBRACE) {
          LexerPosition position = lexer.getCurrentPosition();
          final TreeElement element = parseDeclaration(lexer, Context.CLASS_CONTEXT);
          if (element != null && (element.getElementType() == METHOD || element.getElementType() == FIELD)) {
            if (!declsAfterEnd) {
              final CompositeElement classExpected = Factory.createErrorElement(JavaErrorMessages.message("expected.class.or.interface"));
              root.rawAddChildren(classExpected);
            }
            declsAfterEnd = true;
            root.rawAddChildren(element);
          }
          else {
            lexer.restore(position);
            break;
          }
        }

        if (declsAfterEnd) {
          expectRBrace(root, lexer);
        }
      }

      return root;
    }

    TreeElement classParameterList = null;
    if (tokenType == LT){
      classParameterList = parseTypeParameterList(lexer);
      tokenType = lexer.getTokenType();
    }

    if (context == Context.FILE_CONTEXT){
      final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.class.or.interface"));
      modifierList.rawInsertAfterMe(errorElement);
      if (classParameterList != null){
        errorElement.rawInsertAfterMe(classParameterList);
      }
      return modifierList;
    }

    final TreeElement first = modifierList;
    TreeElement last = modifierList;

    TreeElement type;
    if (tokenType != null && PRIMITIVE_TYPE_BIT_SET.contains(tokenType)){
      type = parseType(lexer);
    }
    else if (tokenType == IDENTIFIER){
      final LexerPosition idPos = lexer.getCurrentPosition();
      type = parseType(lexer);
      if (lexer.getTokenType() == LPARENTH){ //constructor
        if (context == Context.CODE_BLOCK_CONTEXT){
          lexer.restore(startPos);
          return null;
        }
        lexer.restore(idPos);
        CompositeElement method = ASTFactory.composite(METHOD);
        method.rawAddChildren(first);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        method.rawAddChildren(classParameterList);
        method.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        if (lexer.getTokenType() != LPARENTH){
          lexer.restore(startPos);
          return null;
        }
        parseMethodFromLparenth(lexer, method, false);
        return method;
      }
    }
    else if (tokenType == LBRACE){
      if (context == Context.CODE_BLOCK_CONTEXT){
        TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type"));
        last.rawInsertAfterMe(errorElement);
        return first;
      }

      TreeElement codeBlock = myContext.getStatementParsing().parseCodeBlock(lexer, false);
      LOG.assertTrue(codeBlock != null);

      CompositeElement initializer = ASTFactory.composite(CLASS_INITIALIZER);
      initializer.rawAddChildren(modifierList);
      if (classParameterList != null){
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        errorElement.rawAddChildren(classParameterList);
        initializer.rawAddChildren(errorElement);
      }
      initializer.rawAddChildren(codeBlock);
      return initializer;
    }
    else{
      CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type"));
      if (classParameterList != null){
        errorElement.rawAddChildren(classParameterList);
      }
      last.rawInsertAfterMe(errorElement);
      return first;
    }

    last.rawInsertAfterMe(type);
    last = type;

    if (lexer.getTokenType() != IDENTIFIER){
      if (context == Context.CODE_BLOCK_CONTEXT && modifierList.getFirstChildNode() == null){
        lexer.restore(startPos);
        return null;
      }
      else {
        if (classParameterList != null) {
          final CompositeElement errorElement1 = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
          errorElement1.rawAddChildren(classParameterList);
          last.rawInsertBeforeMe(errorElement1);
        }
        TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
        last.rawInsertAfterMe(errorElement);
        return first;
      }
    }

    TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    last.rawInsertAfterMe(identifier);

    if (lexer.getTokenType() == LPARENTH){
      if (context == Context.CLASS_CONTEXT){ //method
        CompositeElement method = ASTFactory.composite(METHOD);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        first.rawInsertAfterMe(classParameterList);
        method.rawAddChildren(first);
        parseMethodFromLparenth(lexer, method, false);
        return method;
      } else if (context == Context.ANNOTATION_INTERFACE_CONTEXT) {
        CompositeElement method = ASTFactory.composite(ANNOTATION_METHOD);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        first.rawInsertAfterMe(classParameterList);
        method.rawAddChildren(first);
        parseMethodFromLparenth(lexer, method, true);
        return method;
      }
      else
        return parseFieldOrLocalVariable(classParameterList, first, context, lexer, startPos);
    }
    else{
      return parseFieldOrLocalVariable(classParameterList, first, context, lexer, startPos);
    }
  }

  private TreeElement parseFieldOrLocalVariable(TreeElement classParameterList,
                                                final TreeElement first,
                                                Context context,
                                                Lexer lexer, LexerPosition startPos) {//field or local variable
    if (classParameterList != null){
      final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
      errorElement.rawAddChildren(classParameterList);
      first.rawInsertAfterMe(errorElement);
    }
    CompositeElement variable;
    if (context == Context.CLASS_CONTEXT || context == Context.ANNOTATION_INTERFACE_CONTEXT){
      variable = ASTFactory.composite(FIELD);
      variable.rawAddChildren(first);
    }
    else if (context == Context.CODE_BLOCK_CONTEXT){
      variable = ASTFactory.composite(LOCAL_VARIABLE);
      variable.rawAddChildren(first);
    }
    else{
      LOG.assertTrue(false);
      return null;
    }

    CompositeElement variable1 = variable;
    boolean unclosed = false;
    boolean eatSemicolon = true;
    boolean shouldRollback;
    while(true){
      shouldRollback = true;
      while(lexer.getTokenType() == LBRACKET){
        variable1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        if (lexer.getTokenType() != RBRACKET){
          variable1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          unclosed = true;
          break;
        }
        variable1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      if (lexer.getTokenType() == EQ){
        variable1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
        if (expr != null){
          shouldRollback = false;
          variable1.rawAddChildren(expr);
        }
        else{
          variable1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          unclosed = true;
          break;
        }
      }

      if (lexer.getTokenType() != COMMA) break;

      TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      variable1.rawInsertAfterMe(comma);

      if (lexer.getTokenType() != IDENTIFIER){
        comma.rawInsertAfterMe(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
        unclosed = true;
        eatSemicolon = false;
        break;
      }

      CompositeElement variable2 = ASTFactory.composite(variable1.getElementType());
      comma.rawInsertAfterMe(variable2);
      variable2.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      variable1 = variable2;
    }

    if (lexer.getTokenType() == SEMICOLON && eatSemicolon){
      variable1.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      // special treatment (see testMultiLineUnclosed())
      if (lexer.getTokenType() != null && shouldRollback){
        int spaceStart = lexer instanceof StoppableLexerAdapter
                         ? ((StoppableLexerAdapter)lexer).getPrevTokenEnd()
                         : ((FilterLexer)lexer).getPrevTokenEnd();
        int spaceEnd = lexer.getTokenStart();
        final CharSequence buffer = lexer.getBufferSequence();
        int lineStart = CharArrayUtil.shiftBackwardUntil(buffer, spaceEnd, "\n\r");

        if (startPos.getOffset() < lineStart && lineStart < spaceStart){
          final int newBufferEnd = CharArrayUtil.shiftForward(buffer, lineStart, "\n\r \t");
          lexer.restore(startPos);
          StoppableLexerAdapter stoppableLexer = new StoppableLexerAdapter(new StoppableLexerAdapter.StoppingCondition() {
            public boolean stopsAt(IElementType token, int start, int end) {
              return start >= newBufferEnd || end > newBufferEnd;
            }
          }, lexer);

          return parseDeclaration(stoppableLexer, context);
        }
      }

      if (!unclosed){
        variable1.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
      }
    }

    return variable;
  }

  public CompositeElement parseAnnotationList (FilterLexer lexer) {
    CompositeElement modifierList = ASTFactory.composite(MODIFIER_LIST);
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (tokenType == AT) {
        final LexerPosition pos = lexer.getCurrentPosition();
        lexer.advance();
        IElementType nextTokenType = lexer.getTokenType();
        lexer.restore(pos);
        if (nextTokenType != null && KEYWORD_BIT_SET.contains(nextTokenType)) {
          break;
        }
        CompositeElement annotation = parseAnnotation (lexer);
        modifierList.rawAddChildren(annotation);
      } else {
        break;
      }
    }

    return modifierList;
  }

  @NotNull
  public CompositeElement parseModifierList (Lexer lexer) {
    CompositeElement modifierList = ASTFactory.composite(MODIFIER_LIST);
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      if (MODIFIER_BIT_SET.contains(tokenType)) {
        modifierList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      } else if (tokenType == AT) {
        final LexerPosition pos = lexer.getCurrentPosition();
        lexer.advance();
        IElementType nextTokenType = lexer.getTokenType();
        lexer.restore(pos);
        if (nextTokenType != null && KEYWORD_BIT_SET.contains(nextTokenType)) {
          break;
        }
        CompositeElement annotation = parseAnnotation (lexer);
        modifierList.rawAddChildren(annotation);
      } else {
        break;
      }
    }

    return modifierList;
  }

  @Nullable
  public CompositeElement parseAnnotationFromText(PsiManager manager, String text, final LanguageLevel languageLevel) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(text);
    CompositeElement first = parseAnnotation(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    dummyRoot.rawAddChildren(first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, text.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  private CompositeElement parseAnnotation(Lexer lexer) {
    CompositeElement annotation = ASTFactory.composite(ANNOTATION);
    if (lexer.getTokenType() == null) return annotation;
    
    annotation.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement classReference = parseJavaCodeReference(lexer, true, false);
    if (classReference != null) {
      annotation.rawAddChildren(classReference);
    } else {
      annotation.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.class.reference")));
    }

    annotation.rawAddChildren(parseAnnotationParameterList(lexer));

    return annotation;
  }

  private CompositeElement parseAnnotationParameterList(Lexer lexer) {
    CompositeElement parameterList = ASTFactory.composite(ANNOTATION_PARAMETER_LIST);
    if (lexer.getTokenType() == LPARENTH) {
      parameterList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      IElementType tokenType = lexer.getTokenType();
      if (tokenType == RPARENTH) {
        parameterList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return parameterList;
      }
      CompositeElement firstParam = parseAnnotationParameter(lexer, true);
      parameterList.rawAddChildren(firstParam);
      boolean isFirstParamNamed = firstParam.getChildRole(firstParam.getFirstChildNode()) == ChildRole.NAME;

      boolean afterBad = false;
      while (true) {
        tokenType = lexer.getTokenType();
        if (tokenType == null) {
          parameterList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.parameter")));
          return parameterList;
        }
        if (tokenType == RPARENTH) {
          parameterList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          return parameterList;
        }
        else if (tokenType == COMMA) {
          TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
          lexer.advance();
          CompositeElement param = parseAnnotationParameter(lexer, false);
          if (!isFirstParamNamed && param != null && param.getChildRole(param.getFirstChildNode()) == ChildRole.NAME) {
            parameterList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("annotation.name.is.missing")));
          }
          parameterList.rawAddChildren(comma);
          parameterList.rawAddChildren(param);
        }
        else if (!afterBad) {
          parameterList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.comma.or.rparen")));
          parameterList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          afterBad = true;
        }
        else {
          afterBad = false;
          parameterList.rawAddChildren(parseAnnotationParameter(lexer, false));
        }
      }
    }
    else {
      return parameterList;
    }
  }

  private CompositeElement parseAnnotationParameter(Lexer lexer, boolean mayBeSimple) {
    CompositeElement pair = ASTFactory.composite(NAME_VALUE_PAIR);
    if (mayBeSimple) {
      final LexerPosition pos = lexer.getCurrentPosition();
      TreeElement value = parseAnnotationMemberValue(lexer);
      if (value != null && lexer.getTokenType() != EQ) {
        pair.rawAddChildren(value);
        return pair;
      }
      else {
        lexer.restore(pos);
      }
    }

    if (lexer.getTokenType() != IDENTIFIER) {
      pair.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
    }
    else {
      pair.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    if (lexer.getTokenType() != EQ) {
      pair.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.eq")));
    }
    else {
      pair.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    pair.rawAddChildren(parseAnnotationMemberValue(lexer));
    return pair;
  }

  private TreeElement parseAnnotationMemberValue(Lexer lexer) {
    TreeElement result;
    if (lexer.getTokenType() == AT) {
      result = parseAnnotation(lexer);
    } else if (lexer.getTokenType() == LBRACE) {
      result = (TreeElement)parseArrayMemberValue (lexer);
    } else {
      result = myContext.getExpressionParsing().parseConditionalExpression(lexer);
    }

    if (result == null) {
      result = Factory.createErrorElement(JavaErrorMessages.message("expected.value"));
    }

    return result;
  }

  private ASTNode parseArrayMemberValue(Lexer lexer) {
    LOG.assertTrue(lexer.getTokenType() == LBRACE);
    CompositeElement memberList = ASTFactory.composite(ANNOTATION_ARRAY_INITIALIZER);
    memberList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    IElementType tokenType = lexer.getTokenType();
    if (tokenType == RBRACE) {
      memberList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return memberList;
    }
    memberList.rawAddChildren(parseAnnotationMemberValue(lexer));


    while (true) {
      tokenType = lexer.getTokenType();
      if (tokenType == RBRACE) {
        memberList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return memberList;
      } else if (tokenType == COMMA) {
        memberList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        memberList.rawAddChildren(parseAnnotationMemberValue(lexer));
        if (lexer.getTokenType() == RBRACE) {
          memberList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          return memberList;
        }
      } else {
        memberList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
        memberList.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
        return memberList;
      }
    }
  }

  private CompositeElement parseClassFromKeyword(Lexer lexer, TreeElement modifierList, TreeElement atToken) {
    CompositeElement aClass = ASTFactory.composite(CLASS);
    aClass.rawAddChildren(modifierList);

    if (atToken != null) {
      aClass.rawAddChildren(atToken);
    }

    final IElementType keywordTokenType = lexer.getTokenType();
    LOG.assertTrue(CLASS_KEYWORD_BIT_SET.contains(keywordTokenType));
    final boolean isEnum = keywordTokenType == ENUM_KEYWORD;
    aClass.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() != IDENTIFIER){
      TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
      aClass.rawAddChildren(errorElement);
      return (CompositeElement)aClass.getFirstChildNode();
    }

    aClass.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement classParameterList = parseTypeParameterList(lexer);
    aClass.rawAddChildren(classParameterList);

    TreeElement extendsList = (TreeElement)parseExtendsList(lexer);
    if (extendsList == null){
      extendsList = ASTFactory.composite(EXTENDS_LIST);
    }
    aClass.rawAddChildren(extendsList);

    TreeElement implementsList = (TreeElement)parseImplementsList(lexer);
    if (implementsList == null){
      implementsList = ASTFactory.composite(IMPLEMENTS_LIST);
    }
    aClass.rawAddChildren(implementsList);

    if (lexer.getTokenType() != LBRACE){
      CompositeElement invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace"));
      aClass.rawAddChildren(invalidElementsGroup);
      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == IDENTIFIER || tokenType == COMMA || tokenType == EXTENDS_KEYWORD || tokenType == IMPLEMENTS_KEYWORD) {
          invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        }
        else {
          break;
        }
        lexer.advance();
      }
    }

    parseClassBodyWithBraces(aClass, lexer, atToken != null, isEnum);

    return aClass;
  }

  private TreeElement parseTypeParameterList(Lexer lexer) {
    final CompositeElement result = ASTFactory.composite(TYPE_PARAMETER_LIST);
    if (lexer.getTokenType() != LT){
      return result;
    }
    result.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    do{
      CompositeElement typeParameter = parseTypeParameter(lexer);
      if (typeParameter == null) {
        typeParameter = Factory.createErrorElement(JavaErrorMessages.message("expected.type.parameter"));
      }
      result.rawAddChildren(typeParameter);
      if(lexer.getTokenType() == COMMA) {
        result.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      } else {
        break;
      }
    } while(true);

    if (lexer.getTokenType() == GT){
      result.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      if (lexer.getTokenType() == IDENTIFIER) {
        // hack for completion
        final LexerPosition position = lexer.getCurrentPosition();
        lexer.advance();
        final IElementType lookahead = lexer.getTokenType();
        lexer.restore(position);
        if (lookahead == GT) {
          final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.identifier"));
          errorElement.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          result.rawAddChildren(errorElement);
          lexer.advance();
          result.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        } else {
          result.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.gt")));
        }
      } else {
        result.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.gt")));
      }
    }

    return result;
  }

  @Nullable
  public TreeElement parseTypeParameterText(CharSequence buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer);
    CompositeElement typeParameter = parseTypeParameter(lexer);
    if (typeParameter == null) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(typeParameter, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return typeParameter;
  }

  @Nullable
  private CompositeElement parseTypeParameter(Lexer lexer) {
    if (lexer.getTokenType() != IDENTIFIER) return null;
    final CompositeElement result = ASTFactory.composite(TYPE_PARAMETER);
    result.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    CompositeElement extendsBound = parseReferenceList(lexer, EXTENDS_BOUND_LIST, AND, EXTENDS_KEYWORD);
    if (extendsBound == null){
      extendsBound = ASTFactory.composite(EXTENDS_BOUND_LIST);
    }
    result.rawAddChildren(extendsBound);
    return result;
  }

  @Nullable
  protected ASTNode parseClassBodyWithBraces(CompositeElement root, final Lexer lexer, boolean annotationInterface, boolean isEnum) {
    if (lexer.getTokenType() != LBRACE) return null;
    LeafElement lbrace = (LeafElement)ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    root.rawAddChildren(lbrace);
    lexer.advance();

    StoppableLexerAdapter classLexer = new StoppableLexerAdapter(new StoppableLexerAdapter.StoppingCondition() {
      private int braceCount = 1;
      public boolean stopsAt(IElementType token, int start, int end) {
        if (token == LBRACE){
          braceCount++;
        }
        else if (token == RBRACE){
          braceCount--;
        }

        return braceCount == 0;

      }
    }, lexer);

    final int context = annotationInterface ? ClassBodyParsing.ANNOTATION : isEnum ? ClassBodyParsing.ENUM : ClassBodyParsing.CLASS;
    myContext.getClassBodyParsing().parseClassBody(root, classLexer, context);

    expectRBrace(root, lexer);
    return lbrace;
  }

  private void expectRBrace(final CompositeElement root, final Lexer lexer) {
    if (lexer.getTokenType() == RBRACE){
      root.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      root.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
    }
  }

  @Nullable
  public TreeElement parseEnumConstant (Lexer lexer) {
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement modifierList = parseModifierList(lexer);
    if (lexer.getTokenType() == IDENTIFIER) {
      final CompositeElement element = ASTFactory.composite(ENUM_CONSTANT);
      element.rawAddChildren(modifierList);
      element.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      CompositeElement argumentList = myContext.getExpressionParsing().parseArgumentList(lexer);
      element.rawAddChildren(argumentList);
      if (lexer.getTokenType() == LBRACE) {
        CompositeElement classElement = ASTFactory.composite(ENUM_CONSTANT_INITIALIZER);
        element.rawAddChildren(classElement);
        parseClassBodyWithBraces(classElement, lexer, false, false);
      }
      return element;
    } else {
      lexer.restore(pos);
      return null;
    }
  }

  private void parseMethodFromLparenth(Lexer lexer, CompositeElement method, boolean annotationMethod) {
    CompositeElement parmList = parseParameterList(lexer);
    method.rawAddChildren(parmList);

    while(lexer.getTokenType() == LBRACKET){
      method.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      if (lexer.getTokenType() != RBRACKET){
        method.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
        break;
      }
      method.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    TreeElement throwsList = (TreeElement)parseThrowsList(lexer);
    if (throwsList == null){
      throwsList = ASTFactory.composite(THROWS_LIST);
    }
    method.rawAddChildren(throwsList);

    if (annotationMethod && lexer.getTokenType() == DEFAULT_KEYWORD) {
      method.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      method.rawAddChildren(parseAnnotationMemberValue(lexer));
    }

    IElementType tokenType = lexer.getTokenType();
    if (tokenType != SEMICOLON && tokenType != LBRACE){
      CompositeElement invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace.or.semicolon"));
      method.rawAddChildren(invalidElementsGroup);
      Loop:
        while(true){
          tokenType = lexer.getTokenType();

          // Heuristic. Going to next line obviously means method signature is over, starting new method.
          // Necessary for correct CompleteStatementTest operation.
          final CharSequence buf = lexer.getBufferSequence();
          int start = lexer.getTokenStart();
          for (int i = start - 1; i >= 0; i--) {
            if (buf.charAt(i) == '\n') break Loop;
            if (buf.charAt(i) != ' ' && buf.charAt(i) != '\t') break;
          }

          if (tokenType == IDENTIFIER || tokenType == COMMA || tokenType == THROWS_KEYWORD) {
            invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          }
          else {
            break;
          }
          lexer.advance();
        }
    }

    if (lexer.getTokenType() == SEMICOLON){
      method.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else if (lexer.getTokenType() == LBRACE){
      TreeElement codeBlock = myContext.getStatementParsing().parseCodeBlock(lexer, false);
      method.rawAddChildren(codeBlock);
    }
  }

  private CompositeElement parseParameterList(Lexer lexer) {
    CompositeElement paramList = ASTFactory.composite(PARAMETER_LIST);
    LOG.assertTrue(lexer.getTokenType() == LPARENTH);
    paramList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement invalidElementsGroup = null;
    boolean commaExpected = false;
    int paramCount = 0;
    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null || tokenType == RPARENTH){
        boolean noLastParm = !commaExpected && paramCount > 0;
        if (noLastParm){
          paramList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type")));
        }

        if (tokenType == RPARENTH){
          paramList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }
        else{
          if (!noLastParm){
            paramList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
          }
        }
        break;
      }

      if (commaExpected){
        if (tokenType == COMMA){
          paramList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          commaExpected = false;
          invalidElementsGroup = null;
          continue;
        }
      }
      else{
        TreeElement param = parseParameter(lexer, true);
        if (param != null){
          paramList.rawAddChildren(param);
          commaExpected = true;
          invalidElementsGroup = null;
          paramCount++;
          continue;
        }
      }

      if (invalidElementsGroup == null){
        if (tokenType == COMMA){
          paramList.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.parameter")));
          paramList.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          continue;
        }
        else{
          invalidElementsGroup = Factory.createErrorElement(commaExpected ?
                                                            JavaErrorMessages.message("expected.comma") :
                                                            JavaErrorMessages.message("expected.parameter"));
          paramList.rawAddChildren(invalidElementsGroup);
        }
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      CompositeElement ref = parseJavaCodeReference(lexer, true, true);
      if (ref != null){
        invalidElementsGroup.rawAddChildren(ref);
      }
      else{
        if (lexer.getTokenType() != null) {
          invalidElementsGroup.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }
      }
    }

    return paramList;
  }

  @Nullable
  private ASTNode parseExtendsList(Lexer lexer) {
    return parseReferenceList(lexer, EXTENDS_LIST, COMMA, EXTENDS_KEYWORD);
  }

  @Nullable
  private ASTNode parseImplementsList(Lexer lexer) {
    return parseReferenceList(lexer, IMPLEMENTS_LIST, COMMA, IMPLEMENTS_KEYWORD);
  }

  @Nullable
  private ASTNode parseThrowsList(Lexer lexer) {
    return parseReferenceList(lexer, THROWS_LIST, COMMA, THROWS_KEYWORD);
  }

  @Nullable
  private CompositeElement parseReferenceList(Lexer lexer, IElementType elementType, IElementType referenceDelimiter, IElementType keyword) {
    if (lexer.getTokenType() != keyword) return null;

    CompositeElement list = ASTFactory.composite(elementType);
    list.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    while (true) {
      TreeElement classReference = parseJavaCodeReference(lexer, true, true);
      if (classReference == null) {
        classReference = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
      }
      list.rawAddChildren(classReference);
      if (lexer.getTokenType() != referenceDelimiter) break;
      TreeElement delimiter = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      list.rawAddChildren(delimiter);
    }

    return list;
  }

  @Nullable
  public CompositeElement parseParameterText(CharSequence buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer);
    ASTNode first = parseParameter(lexer, true);
    if (first == null || first.getElementType() != PARAMETER) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens((CompositeElement)first, originalLexer, 0, buffer.length(), -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (CompositeElement)first;
  }

  @Nullable
  public TreeElement parseParameter(Lexer lexer, boolean allowEllipsis) {
    final LexerPosition pos = lexer.getCurrentPosition();

    CompositeElement modifierList = parseModifierList(lexer);

    CompositeElement type = allowEllipsis ? parseTypeWithEllipsis(lexer) : parseType(lexer);
    if (type == null && modifierList.getFirstChildNode() == null){
      lexer.restore(pos);
      return null;
    }

    CompositeElement param = ASTFactory.composite(PARAMETER);
    param.rawAddChildren(modifierList);

    if (type == null) {
      type = ASTFactory.composite(TYPE);
      param.rawAddChildren(Factory.createErrorElement("Parameter type missing"));
    }

    param.rawAddChildren(type);

    if (lexer.getTokenType() == IDENTIFIER){
      param.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      while(lexer.getTokenType() == LBRACKET){
        param.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        if (lexer.getTokenType() != RBRACKET){
          param.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          break;
        }
        param.rawAddChildren(ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      return param;
    } else{
      param.rawAddChildren(Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return param.getFirstChildNode();
    }
  }
}
