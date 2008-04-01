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
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
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
    lexer.start(buffer, 0, buffer.length(),0);
    return parseEnumConstant(lexer);
  }

  public TreeElement parseDeclarationText(PsiManager manager,
                                          LanguageLevel languageLevel,
                                          CharSequence buffer,
                                          Context context) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length(), 0);
    TreeElement first = parseDeclaration(lexer, context);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  public TreeElement parseMemberValueText(PsiManager manager, CharSequence buffer, final LanguageLevel languageLevel) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length(), 0);
    TreeElement first = parseAnnotationMemberValue(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length(), -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
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
    else if (tokenType instanceof IChameleonElementType) {
      LeafElement declaration =
        ASTFactory.leaf(tokenType, lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(), myContext.getCharTable());
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
        final CompositeElement classExpected = Factory.createErrorElement(JavaErrorMessages.message("expected.class.or.interface"));
        TreeUtil.addChildren(root, classExpected);
        boolean declsAfterEnd = false;

        while (lexer.getTokenType() != null && lexer.getTokenType() != RBRACE) {
          LexerPosition position = lexer.getCurrentPosition();
          final TreeElement element = parseDeclaration(lexer, Context.CLASS_CONTEXT);
          if (element != null && (element.getElementType() == METHOD || element.getElementType() == FIELD)) {
            declsAfterEnd = true;
            TreeUtil.addChildren(root, element);
          } else {
            lexer.restore(position);
            break;
          }
        }

        if (!declsAfterEnd) {
          TreeUtil.remove(classExpected);
        } else {
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
      TreeUtil.insertAfter(modifierList, errorElement);
      if (classParameterList != null){
        TreeUtil.insertAfter(errorElement, classParameterList);
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
        TreeUtil.addChildren(method, first);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        TreeUtil.addChildren(method, classParameterList);
        TreeUtil.addChildren(method, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
        TreeUtil.insertAfter(last, errorElement);
        return first;
      }

      TreeElement codeBlock = myContext.getStatementParsing().parseCodeBlock(lexer, false);
      LOG.assertTrue(codeBlock != null);

      CompositeElement initializer = ASTFactory.composite(CLASS_INITIALIZER);
      TreeUtil.addChildren(initializer, modifierList);
      if (classParameterList != null){
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        TreeUtil.addChildren(errorElement, classParameterList);
        TreeUtil.addChildren(initializer, errorElement);
      }
      TreeUtil.addChildren(initializer, codeBlock);
      return initializer;
    }
    else{
      CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type"));
      if (classParameterList != null){
        TreeUtil.addChildren(errorElement, classParameterList);
      }
      TreeUtil.insertAfter(last, errorElement);
      return first;
    }

    TreeUtil.insertAfter(last, type);
    last = type;

    if (lexer.getTokenType() != IDENTIFIER){
      if (context == Context.CODE_BLOCK_CONTEXT && modifierList.getFirstChildNode() == null){
        lexer.restore(startPos);
        return null;
      }
      else{
        TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
        if (classParameterList != null){
          final CompositeElement errorElement1 = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
          TreeUtil.addChildren(errorElement1, classParameterList);
          TreeUtil.insertBefore(last, errorElement1);
        }
        TreeUtil.insertAfter(last, errorElement);
        return first;
      }
    }

    TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    TreeUtil.insertAfter(last, identifier);

    if (lexer.getTokenType() == LPARENTH){
      if (context == Context.CLASS_CONTEXT){ //method
        CompositeElement method = ASTFactory.composite(METHOD);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        TreeUtil.insertAfter(first, classParameterList);
        TreeUtil.addChildren(method, first);
        parseMethodFromLparenth(lexer, method, false);
        return method;
      } else if (context == Context.ANNOTATION_INTERFACE_CONTEXT) {
        CompositeElement method = ASTFactory.composite(ANNOTATION_METHOD);
        if (classParameterList == null){
          classParameterList = ASTFactory.composite(TYPE_PARAMETER_LIST);
        }
        TreeUtil.insertAfter(first, classParameterList);
        TreeUtil.addChildren(method, first);
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
      TreeUtil.addChildren(errorElement, classParameterList);
      TreeUtil.insertAfter(first, errorElement);
    }
    CompositeElement variable;
    if (context == Context.CLASS_CONTEXT || context == Context.ANNOTATION_INTERFACE_CONTEXT){
      variable = ASTFactory.composite(FIELD);
      TreeUtil.addChildren(variable, first);
    }
    else if (context == Context.CODE_BLOCK_CONTEXT){
      variable = ASTFactory.composite(LOCAL_VARIABLE);
      TreeUtil.addChildren(variable, first);
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
        TreeUtil.addChildren(variable1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        if (lexer.getTokenType() != RBRACKET){
          TreeUtil.addChildren(variable1, Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          unclosed = true;
          break;
        }
        TreeUtil.addChildren(variable1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      if (lexer.getTokenType() == EQ){
        TreeUtil.addChildren(variable1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();

        TreeElement expr = myContext.getExpressionParsing().parseExpression(lexer);
        if (expr != null){
          shouldRollback = false;
          TreeUtil.addChildren(variable1, expr);
        }
        else{
          TreeUtil.addChildren(variable1, Factory.createErrorElement(JavaErrorMessages.message("expected.expression")));
          unclosed = true;
          break;
        }
      }

      if (lexer.getTokenType() != COMMA) break;

      TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      TreeUtil.insertAfter(variable1, comma);

      if (lexer.getTokenType() != IDENTIFIER){
        TreeUtil.insertAfter(comma, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
        unclosed = true;
        eatSemicolon = false;
        break;
      }

      CompositeElement variable2 = ASTFactory.composite(variable1.getElementType());
      TreeUtil.insertAfter(comma, variable2);
      TreeUtil.addChildren(variable2, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      variable1 = variable2;
    }

    if (lexer.getTokenType() == SEMICOLON && eatSemicolon){
      TreeUtil.addChildren(variable1, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
        TreeUtil.addChildren(variable1, Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
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
        TreeUtil.addChildren(modifierList, annotation);
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
        TreeUtil.addChildren(modifierList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
        TreeUtil.addChildren(modifierList, annotation);
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
    lexer.start(text, 0, text.length(),0);
    CompositeElement first = parseAnnotation(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();
    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, text.length(), -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  private CompositeElement parseAnnotation(Lexer lexer) {
    CompositeElement annotation = ASTFactory.composite(ANNOTATION);
    if (lexer.getTokenType() == null) return annotation;
    
    TreeUtil.addChildren(annotation, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement classReference = parseJavaCodeReference(lexer, true, false);
    if (classReference != null) {
      TreeUtil.addChildren(annotation, classReference);
    } else {
      TreeUtil.addChildren(annotation, Factory.createErrorElement(JavaErrorMessages.message("expected.class.reference")));
    }

    TreeUtil.addChildren(annotation, parseAnnotationParameterList(lexer));

    return annotation;
  }

  private CompositeElement parseAnnotationParameterList(Lexer lexer) {
    CompositeElement parameterList = ASTFactory.composite(ANNOTATION_PARAMETER_LIST);
    if (lexer.getTokenType() == LPARENTH) {
      TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      IElementType tokenType = lexer.getTokenType();
      if (tokenType == RPARENTH) {
        TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return parameterList;
      }
      CompositeElement firstParam = parseAnnotationParameter(lexer, true);
      TreeUtil.addChildren(parameterList, firstParam);
      boolean isFirstParamNamed = firstParam.getChildRole(firstParam.getFirstChildNode()) == ChildRole.NAME;

      boolean afterBad = false;
      while (true) {
        tokenType = lexer.getTokenType();
        if (tokenType == null) {
          TreeUtil.addChildren(parameterList, Factory.createErrorElement(JavaErrorMessages.message("expected.parameter")));
          return parameterList;
        }
        if (tokenType == RPARENTH) {
          TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          return parameterList;
        }
        else if (tokenType == COMMA) {
          TreeElement comma = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
          lexer.advance();
          CompositeElement param = parseAnnotationParameter(lexer, false);
          if (!isFirstParamNamed && param != null && param.getChildRole(param.getFirstChildNode()) == ChildRole.NAME) {
            TreeUtil.addChildren(parameterList, Factory.createErrorElement(JavaErrorMessages.message("annotation.name.is.missing")));
          }
          TreeUtil.addChildren(parameterList, comma);
          TreeUtil.addChildren(parameterList, param);
        }
        else if (!afterBad) {
          TreeUtil.addChildren(parameterList, Factory.createErrorElement(JavaErrorMessages.message("expected.comma.or.rparen")));
          TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          afterBad = true;
        }
        else {
          afterBad = false;
          TreeUtil.addChildren(parameterList, parseAnnotationParameter(lexer, false));
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
        TreeUtil.addChildren(pair, value);
        return pair;
      }
      else {
        lexer.restore(pos);
      }
    }

    if (lexer.getTokenType() != IDENTIFIER) {
      TreeUtil.addChildren(pair, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
    }
    else {
      TreeUtil.addChildren(pair, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    if (lexer.getTokenType() != EQ) {
      TreeUtil.addChildren(pair, Factory.createErrorElement(JavaErrorMessages.message("expected.eq")));
    }
    else {
      TreeUtil.addChildren(pair, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    TreeUtil.addChildren(pair, parseAnnotationMemberValue(lexer));
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
    TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    IElementType tokenType = lexer.getTokenType();
    if (tokenType == RBRACE) {
      TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return memberList;
    }
    TreeUtil.addChildren(memberList, parseAnnotationMemberValue(lexer));


    while (true) {
      tokenType = lexer.getTokenType();
      if (tokenType == RBRACE) {
        TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return memberList;
      } else if (tokenType == COMMA) {
        TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        TreeUtil.addChildren(memberList, parseAnnotationMemberValue(lexer));
        if (lexer.getTokenType() == RBRACE) {
          TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          return memberList;
        }
      } else {
        TreeUtil.addChildren(memberList, Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
        memberList.putUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY, "");
        return memberList;
      }
    }
  }

  private CompositeElement parseClassFromKeyword(Lexer lexer, TreeElement modifierList, TreeElement atToken) {
    CompositeElement aClass = ASTFactory.composite(CLASS);
    TreeUtil.addChildren(aClass, modifierList);

    if (atToken != null) {
      TreeUtil.addChildren(aClass, atToken);
    }

    final IElementType keywordTokenType = lexer.getTokenType();
    LOG.assertTrue(CLASS_KEYWORD_BIT_SET.contains(keywordTokenType));
    final boolean isEnum = keywordTokenType == ENUM_KEYWORD;
    TreeUtil.addChildren(aClass, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    if (lexer.getTokenType() != IDENTIFIER){
      TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
      TreeUtil.addChildren(aClass, errorElement);
      return (CompositeElement)aClass.getFirstChildNode();
    }

    TreeUtil.addChildren(aClass, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement classParameterList = parseTypeParameterList(lexer);
    TreeUtil.addChildren(aClass, classParameterList);

    TreeElement extendsList = (TreeElement)parseExtendsList(lexer);
    if (extendsList == null){
      extendsList = ASTFactory.composite(EXTENDS_LIST);
    }
    TreeUtil.addChildren(aClass, extendsList);

    TreeElement implementsList = (TreeElement)parseImplementsList(lexer);
    if (implementsList == null){
      implementsList = ASTFactory.composite(IMPLEMENTS_LIST);
    }
    TreeUtil.addChildren(aClass, implementsList);

    if (lexer.getTokenType() != LBRACE){
      CompositeElement invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace"));
      TreeUtil.addChildren(aClass, invalidElementsGroup);
      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == IDENTIFIER || tokenType == COMMA || tokenType == EXTENDS_KEYWORD || tokenType == IMPLEMENTS_KEYWORD) {
          TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
    TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    do{
      CompositeElement typeParameter = parseTypeParameter(lexer);
      if (typeParameter == null) {
        typeParameter = Factory.createErrorElement(JavaErrorMessages.message("expected.type.parameter"));
      }
      TreeUtil.addChildren(result, typeParameter);
      if(lexer.getTokenType() == COMMA) {
        TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      } else {
        break;
      }
    } while(true);

    if (lexer.getTokenType() == GT){
      TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
          TreeUtil.addChildren(errorElement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          TreeUtil.addChildren(result, errorElement);
          lexer.advance();
          TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        } else {
          TreeUtil.addChildren(result, Factory.createErrorElement(JavaErrorMessages.message("expected.gt")));
        }
      } else {
        TreeUtil.addChildren(result, Factory.createErrorElement(JavaErrorMessages.message("expected.gt")));
      }
    }

    return result;
  }

  @Nullable
  public TreeElement parseTypeParameterText(CharSequence buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length(),0);
    CompositeElement typeParameter = parseTypeParameter(lexer);
    if (typeParameter == null) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(typeParameter, originalLexer, 0, buffer.length(), -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return typeParameter;
  }

  @Nullable
  private CompositeElement parseTypeParameter(Lexer lexer) {
    if (lexer.getTokenType() != IDENTIFIER) return null;
    final CompositeElement result = ASTFactory.composite(TYPE_PARAMETER);
    TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    CompositeElement extendsBound = parseReferenceList(lexer, EXTENDS_BOUND_LIST, AND, EXTENDS_KEYWORD);
    if (extendsBound == null){
      extendsBound = ASTFactory.composite(EXTENDS_BOUND_LIST);
    }
    TreeUtil.addChildren(result, extendsBound);
    return result;
  }

  @Nullable
  protected ASTNode parseClassBodyWithBraces(CompositeElement root, final Lexer lexer, boolean annotationInterface, boolean isEnum) {
    if (lexer.getTokenType() != LBRACE) return null;
    LeafElement lbrace = (LeafElement)ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    TreeUtil.addChildren(root, lbrace);
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
      TreeUtil.addChildren(root, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      TreeUtil.addChildren(root, Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
    }
  }

  @Nullable
  public TreeElement parseEnumConstant (Lexer lexer) {
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement modifierList = parseModifierList(lexer);
    if (lexer.getTokenType() == IDENTIFIER) {
      final CompositeElement element = ASTFactory.composite(ENUM_CONSTANT);
      TreeUtil.addChildren(element, modifierList);
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      CompositeElement argumentList = myContext.getExpressionParsing().parseArgumentList(lexer);
      TreeUtil.addChildren(element, argumentList);
      if (lexer.getTokenType() == LBRACE) {
        CompositeElement classElement = ASTFactory.composite(ENUM_CONSTANT_INITIALIZER);
        TreeUtil.addChildren(element, classElement);
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
    TreeUtil.addChildren(method, parmList);

    while(lexer.getTokenType() == LBRACKET){
      TreeUtil.addChildren(method, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      if (lexer.getTokenType() != RBRACKET){
        TreeUtil.addChildren(method, Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
        break;
      }
      TreeUtil.addChildren(method, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    TreeElement throwsList = (TreeElement)parseThrowsList(lexer);
    if (throwsList == null){
      throwsList = ASTFactory.composite(THROWS_LIST);
    }
    TreeUtil.addChildren(method, throwsList);

    if (annotationMethod && lexer.getTokenType() == DEFAULT_KEYWORD) {
      TreeUtil.addChildren(method, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      TreeUtil.addChildren(method, parseAnnotationMemberValue(lexer));
    }

    IElementType tokenType = lexer.getTokenType();
    if (tokenType != SEMICOLON && tokenType != LBRACE){
      CompositeElement invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace.or.semicolon"));
      TreeUtil.addChildren(method, invalidElementsGroup);
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
            TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          }
          else {
            break;
          }
          lexer.advance();
        }
    }

    if (lexer.getTokenType() == SEMICOLON){
      TreeUtil.addChildren(method, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else if (lexer.getTokenType() == LBRACE){
      TreeElement codeBlock = myContext.getStatementParsing().parseCodeBlock(lexer, false);
      TreeUtil.addChildren(method, codeBlock);
    }
  }

  private CompositeElement parseParameterList(Lexer lexer) {
    CompositeElement paramList = ASTFactory.composite(PARAMETER_LIST);
    LOG.assertTrue(lexer.getTokenType() == LPARENTH);
    TreeUtil.addChildren(paramList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    CompositeElement invalidElementsGroup = null;
    boolean commaExpected = false;
    int paramCount = 0;
    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null || tokenType == RPARENTH){
        boolean noLastParm = !commaExpected && paramCount > 0;
        if (noLastParm){
          TreeUtil.addChildren(paramList, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type")));
        }

        if (tokenType == RPARENTH){
          TreeUtil.addChildren(paramList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
        }
        else{
          if (!noLastParm){
            TreeUtil.addChildren(paramList, Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
          }
        }
        break;
      }

      if (commaExpected){
        if (tokenType == COMMA){
          TreeUtil.addChildren(paramList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          commaExpected = false;
          invalidElementsGroup = null;
          continue;
        }
      }
      else{
        TreeElement param = parseParameter(lexer, true);
        if (param != null){
          TreeUtil.addChildren(paramList, param);
          commaExpected = true;
          invalidElementsGroup = null;
          paramCount++;
          continue;
        }
      }

      if (invalidElementsGroup == null){
        if (tokenType == COMMA){
          TreeUtil.addChildren(paramList, Factory.createErrorElement(JavaErrorMessages.message("expected.parameter")));
          TreeUtil.addChildren(paramList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          continue;
        }
        else{
          invalidElementsGroup = Factory.createErrorElement(commaExpected ?
                                                            JavaErrorMessages.message("expected.comma") :
                                                            JavaErrorMessages.message("expected.parameter"));
          TreeUtil.addChildren(paramList, invalidElementsGroup);
        }
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      CompositeElement ref = parseJavaCodeReference(lexer, true, true);
      if (ref != null){
        TreeUtil.addChildren(invalidElementsGroup, ref);
      }
      else{
        if (lexer.getTokenType() != null) {
          TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
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
    TreeUtil.addChildren(list, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    while (true) {
      TreeElement classReference = parseJavaCodeReference(lexer, true, true);
      if (classReference == null) {
        classReference = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier"));
      }
      TreeUtil.addChildren(list, classReference);
      if (lexer.getTokenType() != referenceDelimiter) break;
      TreeElement delimiter = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();
      TreeUtil.addChildren(list, delimiter);
    }

    return list;
  }

  @Nullable
  public CompositeElement parseParameterText(CharSequence buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length(), 0);
    ASTNode first = parseParameter(lexer, true);
    if (first == null || first.getElementType() != PARAMETER) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens((CompositeElement)first, originalLexer, 0, buffer.length(), -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (CompositeElement)first;
  }

  @Nullable
  public TreeElement parseParameter(Lexer lexer, boolean allowEllipsis) {
    final LexerPosition pos = lexer.getCurrentPosition();

    CompositeElement modifierList = parseModifierList(lexer);

    CompositeElement type = allowEllipsis ? parseTypeWithEllipsis(lexer) : parseType(lexer);
    if (type == null){
      lexer.restore(pos);
      return null;
    }

    CompositeElement param = ASTFactory.composite(PARAMETER);
    TreeUtil.addChildren(param, modifierList);
    TreeUtil.addChildren(param, type);

    if (lexer.getTokenType() == IDENTIFIER){
      TreeUtil.addChildren(param, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      while(lexer.getTokenType() == LBRACKET){
        TreeUtil.addChildren(param, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        if (lexer.getTokenType() != RBRACKET){
          TreeUtil.addChildren(param, Factory.createErrorElement(JavaErrorMessages.message("expected.rbracket")));
          break;
        }
        TreeUtil.addChildren(param, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }

      return param;
    } else{
      TreeUtil.addChildren(param, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return param.getFirstChildNode();
    }
  }
}
