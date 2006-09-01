package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import static com.intellij.psi.impl.source.parsing.DeclarationParsing.Context.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

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

  public TreeElement parseEnumConstantText(PsiManager manager, char[] buffer) {
    Lexer originalLexer = new JavaLexer(LanguageLevel.JDK_1_5);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length);
    return parseEnumConstant(lexer);
  }

  public TreeElement parseDeclarationText(PsiManager manager,
                                          LanguageLevel languageLevel,
                                          char[] buffer,
                                          Context context) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length);
    TreeElement first = parseDeclaration(lexer, context);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  public TreeElement parseMemberValueText(PsiManager manager, char[] buffer, final LanguageLevel languageLevel) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length);
    TreeElement first = parseAnnotationMemberValue(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  protected TreeElement parseDeclaration(Lexer lexer, Context context) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == LBRACE){
      if (context == FILE_CONTEXT || context == CODE_BLOCK_CONTEXT) return null;
    }
    else if (tokenType == IDENTIFIER || PRIMITIVE_TYPE_BIT_SET.contains(tokenType)){
      if (context == FILE_CONTEXT) return null;
    }
    else if (tokenType instanceof IChameleonElementType) {
      LeafElement declaration = Factory.createLeafElement(tokenType, lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState(), myContext.getCharTable());
      lexer.advance();
      return declaration;
    }
    else if (!MODIFIER_BIT_SET.contains(tokenType) && !CLASS_KEYWORD_BIT_SET.contains(tokenType)
             && tokenType != AT && (context == CODE_BLOCK_CONTEXT || tokenType != LT)){
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
    } else if (CLASS_KEYWORD_BIT_SET.contains(tokenType)){
      return parseClassFromKeyword(lexer, modifierList, null);
    }

    TreeElement classParameterList = null;
    if (tokenType == LT){
      classParameterList = parseTypeParameterList(lexer);
      tokenType = lexer.getTokenType();
    }

    if (context == FILE_CONTEXT){
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
        if (context == CODE_BLOCK_CONTEXT){
          lexer.restore(startPos);
          return null;
        }
        lexer.restore(idPos);
        CompositeElement method = Factory.createCompositeElement(METHOD);
        TreeUtil.addChildren(method, first);
        if (classParameterList == null){
          classParameterList = Factory.createCompositeElement(TYPE_PARAMETER_LIST);
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
      if (context == CODE_BLOCK_CONTEXT){
        TreeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("expected.identifier.or.type"));
        TreeUtil.insertAfter(last, errorElement);
        last = errorElement;
        return first;
      }

      TreeElement codeBlock = myContext.getStatementParsing().parseCodeBlock(lexer, false);
      LOG.assertTrue(codeBlock != null);

      CompositeElement initializer = Factory.createCompositeElement(CLASS_INITIALIZER);
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
      last = errorElement;
      return first;
    }

    TreeUtil.insertAfter(last, type);
    last = type;

    if (lexer.getTokenType() != IDENTIFIER){
      if (context == CODE_BLOCK_CONTEXT && modifierList.getFirstChildNode() == null){
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
        last = errorElement;
        return first;
      }
    }

    TreeElement identifier = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();

    TreeUtil.insertAfter(last, identifier);
    last = identifier;

    if (lexer.getTokenType() == LPARENTH){
      if (context == CLASS_CONTEXT){ //method
        CompositeElement method = Factory.createCompositeElement(METHOD);
        if (classParameterList == null){
          classParameterList = Factory.createCompositeElement(TYPE_PARAMETER_LIST);
        }
        TreeUtil.insertAfter(first, classParameterList);
        TreeUtil.addChildren(method, first);
        parseMethodFromLparenth(lexer, method, false);
        return method;
      } else if (context == ANNOTATION_INTERFACE_CONTEXT) {
        CompositeElement method = Factory.createCompositeElement(ANNOTATION_METHOD);
        if (classParameterList == null){
          classParameterList = Factory.createCompositeElement(TYPE_PARAMETER_LIST);
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
    if (context == CLASS_CONTEXT || context == ANNOTATION_INTERFACE_CONTEXT){
      variable = Factory.createCompositeElement(FIELD);
      TreeUtil.addChildren(variable, first);
    }
    else if (context == CODE_BLOCK_CONTEXT){
      variable = Factory.createCompositeElement(LOCAL_VARIABLE);
      TreeUtil.addChildren(variable, first);
    }
    else{
      LOG.assertTrue(false);
      return null;
    }

    CompositeElement variable1 = variable;
    boolean unclosed = false;
    boolean eatSemicolon = true;
    while(true){
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

      CompositeElement variable2 = Factory.createCompositeElement(variable1.getElementType());
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
      if (lexer.getTokenType() != null){
        int spaceStart = ((FilterLexer)lexer).getPrevTokenEnd();
        int spaceEnd = lexer.getTokenStart();
        char[] buffer = lexer.getBuffer();
        int lineStart = CharArrayUtil.shiftBackwardUntil(buffer, spaceEnd, "\n\r");
        if (startPos.getOffset() < lineStart && lineStart < spaceStart){
          lexer.restore(startPos);
          int bufferEnd = lexer.getBufferEnd();
          int newBufferEnd = CharArrayUtil.shiftForward(buffer, lineStart, "\n\r \t");
          lexer.start(buffer, startPos.getOffset(), newBufferEnd, ((SimpleLexerState)startPos.getState()).getState());
          TreeElement result = parseDeclaration(lexer, context);
          lexer.start(buffer, lexer.getTokenStart(), bufferEnd, lexer.getState());
          return result;
        }
      }

      if (!unclosed){
        TreeUtil.addChildren(variable1, Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
      }
    }

    return variable;
  }

  public CompositeElement parseAnnotationList (FilterLexer lexer) {
    CompositeElement modifierList = Factory.createCompositeElement(MODIFIER_LIST);
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
    CompositeElement modifierList = Factory.createCompositeElement(MODIFIER_LIST);
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

  public CompositeElement parseAnnotationFromText(PsiManager manager, String text, final LanguageLevel languageLevel) {
    Lexer originalLexer = new JavaLexer(languageLevel);
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    char[] buffer = text.toCharArray();
    lexer.start(buffer, 0, buffer.length);
    CompositeElement first = parseAnnotation(lexer);
    if (first == null) return null;
    if (lexer.getTokenType() != null) return null;

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    TreeUtil.addChildren(dummyRoot, first);

    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return first;
  }

  private CompositeElement parseAnnotation(Lexer lexer) {
    CompositeElement annotation = Factory.createCompositeElement(ANNOTATION);
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
    CompositeElement parameterList = Factory.createCompositeElement(ANNOTATION_PARAMETER_LIST);
    if (lexer.getTokenType() == LPARENTH) {
      TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();

      IElementType tokenType = lexer.getTokenType();
      if (tokenType == RPARENTH) {
        TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return parameterList;
      }
      else {
        TreeUtil.addChildren(parameterList, parseAnnotationParameter(lexer, true));
      }


      while (true) {
        tokenType = lexer.getTokenType();
        if (tokenType == RPARENTH) {
          TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          return parameterList;
        }
        else if (tokenType == COMMA) {
          TreeUtil.addChildren(parameterList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          lexer.advance();
          TreeUtil.addChildren(parameterList, parseAnnotationParameter(lexer, false));
        }
        else {
          TreeUtil.addChildren(parameterList, Factory.createErrorElement(JavaErrorMessages.message("expected.rparen")));
          parameterList.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
          return parameterList;
        }
      }
    }
    else {
      return parameterList;
    }
  }

  private CompositeElement parseAnnotationParameter(Lexer lexer, boolean mayBeSimple) {
    CompositeElement pair = Factory.createCompositeElement(NAME_VALUE_PAIR);
    if (mayBeSimple) {
      final LexerPosition pos = lexer.getCurrentPosition();
      TreeElement value = parseAnnotationMemberValue(lexer);
      if (value != null && lexer.getTokenType() == RPARENTH) {
        TreeUtil.addChildren(pair, value);
        return pair;
      } else {
        lexer.restore(pos);
      }
    }

    if (lexer.getTokenType() != IDENTIFIER) {
      TreeUtil.addChildren(pair, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
    } else {
      TreeUtil.addChildren(pair, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }

    if (lexer.getTokenType() != EQ) {
      TreeUtil.addChildren(pair, Factory.createErrorElement(JavaErrorMessages.message("expected.eq")));
    } else {
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
    CompositeElement memberList = Factory.createCompositeElement(ANNOTATION_ARRAY_INITIALIZER);
    TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    IElementType tokenType = lexer.getTokenType();
    if (tokenType == RBRACE) {
      TreeUtil.addChildren(memberList, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      return memberList;
    } else {
      TreeUtil.addChildren(memberList, parseAnnotationMemberValue(lexer));
    }


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
        memberList.putUserData(ParseUtil.UNCLOSED_ELEMENT_PROPERTY, "");
        return memberList;
      }
    }
  }

  private TreeElement parseClassFromKeyword(Lexer lexer, TreeElement modifierList, TreeElement atToken) {
    IElementType tokenType;
    CompositeElement aClass = Factory.createCompositeElement(CLASS);
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
      return (TreeElement)aClass.getFirstChildNode();
    }

    TreeUtil.addChildren(aClass, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();

    TreeElement classParameterList = parseTypeParameterList(lexer);
    TreeUtil.addChildren(aClass, classParameterList);

    if (!isEnum) {
      TreeElement extendsList = (TreeElement)parseExtendsList(lexer);
      if (extendsList == null){
        extendsList = Factory.createCompositeElement(EXTENDS_LIST);
      }
      TreeUtil.addChildren(aClass, extendsList);
    }

    TreeElement implementsList = (TreeElement)parseImplementsList(lexer);
    if (implementsList == null){
      implementsList = Factory.createCompositeElement(IMPLEMENTS_LIST);
    }
    TreeUtil.addChildren(aClass, implementsList);

    if (lexer.getTokenType() != LBRACE){
      CompositeElement invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.lbrace"));
      TreeUtil.addChildren(aClass, invalidElementsGroup);
      Loop:
        while(true){
          tokenType = lexer.getTokenType();
          if (tokenType == IDENTIFIER || tokenType == COMMA || tokenType == EXTENDS_KEYWORD || tokenType == IMPLEMENTS_KEYWORD) {
            TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          }
          else {
            break Loop;
          }
          lexer.advance();
        }
    }

    parseClassBodyWithBraces(aClass, lexer, atToken != null, isEnum);

    return aClass;
  }

  public TreeElement parseTypeParameterList(Lexer lexer) {
    final CompositeElement result = Factory.createCompositeElement(TYPE_PARAMETER_LIST);
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

  public TreeElement parseTypeParameterText(char[] buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length);
    CompositeElement typeParameter = parseTypeParameter(lexer);
    if (typeParameter == null) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens(typeParameter, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return typeParameter;
  }

  public CompositeElement parseTypeParameter(Lexer lexer) {
    if (lexer.getTokenType() != IDENTIFIER) return null;
    final CompositeElement result = Factory.createCompositeElement(TYPE_PARAMETER);
    TreeUtil.addChildren(result, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    CompositeElement extendsBound = parseReferenceList(lexer, EXTENDS_BOUND_LIST, AND, EXTENDS_KEYWORD);
    if (extendsBound == null){
      extendsBound = Factory.createCompositeElement(EXTENDS_BOUND_LIST);
    }
    TreeUtil.addChildren(result, extendsBound);
    return result;
  }

  protected ASTNode parseClassBodyWithBraces(CompositeElement root, Lexer lexer, boolean annotationInterface, boolean isEnum) {
    if (lexer.getTokenType() != LBRACE) return null;
    LeafElement lbrace = (LeafElement)ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    TreeUtil.addChildren(root, lbrace);
    lexer.advance();
    final int chameleonStart = lexer.getTokenStart();
    final int state = lexer.getState();
    LeafElement rbrace = null;
    int braceCount = 1;
    int chameleonEnd = chameleonStart;
    while(braceCount > 0){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null)
        break;
      if (tokenType == LBRACE){
        braceCount++;
      }
      else if (tokenType == RBRACE){
        braceCount--;
      }
      if (braceCount == 0){
        rbrace = (LeafElement)ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      }
      chameleonEnd = lexer.getTokenEnd();
      lexer.advance();
    }
    final int context = annotationInterface ? ClassBodyParsing.ANNOTATION : isEnum ? ClassBodyParsing.ENUM : ClassBodyParsing.CLASS;
    final int bufferEnd = lexer.getBufferEnd();
    final int endOffset = rbrace != null ? chameleonEnd - 1: bufferEnd;

    lexer.start(lexer.getBuffer(), chameleonStart, endOffset, state);
    myContext.getClassBodyParsing().parseClassBody(root, lexer, context);
    lexer.start(lexer.getBuffer(), chameleonStart, bufferEnd, state);
    if (rbrace != null){
      TreeUtil.addChildren(root, rbrace);
      while(lexer.getTokenStart() < chameleonEnd) lexer.advance();
    }
    else{
      TreeUtil.addChildren(root, Factory.createErrorElement(JavaErrorMessages.message("expected.rbrace")));
      while(lexer.getTokenStart() < endOffset) lexer.advance();
    }

    return lbrace;
  }

  public TreeElement parseEnumConstant (Lexer lexer) {
    final LexerPosition pos = lexer.getCurrentPosition();
    CompositeElement modifierList = parseModifierList(lexer);
    if (lexer.getTokenType() == IDENTIFIER) {
      final CompositeElement element = Factory.createCompositeElement(ENUM_CONSTANT);
      TreeUtil.addChildren(element, modifierList);
      TreeUtil.addChildren(element, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      CompositeElement argumentList = myContext.getExpressionParsing().parseArgumentList(lexer);
      TreeUtil.addChildren(element, argumentList);
      if (lexer.getTokenType() == LBRACE) {
        CompositeElement classElement = Factory.createCompositeElement(ENUM_CONSTANT_INITIALIZER);
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
      throwsList = Factory.createCompositeElement(THROWS_LIST);
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
          char[] buf = lexer.getBuffer();
          int start = lexer.getTokenStart();
          for (int i = start - 1; i >= 0; i--) {
            if (buf[i] == '\n') break Loop;
            if (buf[i] != ' ' && buf[i] != '\t') break;
          }

          if (tokenType == IDENTIFIER || tokenType == COMMA || tokenType == THROWS_KEYWORD) {
            TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
          }
          else {
            break Loop;
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

  public CompositeElement parseParameterList(Lexer lexer) {
    CompositeElement paramList = Factory.createCompositeElement(PARAMETER_LIST);
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
        TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
    }

    return paramList;
  }

  public ASTNode parseExtendsList(Lexer lexer) {
    return parseReferenceList(lexer, EXTENDS_LIST, COMMA, EXTENDS_KEYWORD);
  }

  public ASTNode parseImplementsList(Lexer lexer) {
    return parseReferenceList(lexer, IMPLEMENTS_LIST, COMMA, IMPLEMENTS_KEYWORD);
  }

  public ASTNode parseThrowsList(Lexer lexer) {
    return parseReferenceList(lexer, THROWS_LIST, COMMA, THROWS_KEYWORD);
  }

  public CompositeElement parseReferenceList(Lexer lexer, IElementType elementType, IElementType referenceDelimiter, IElementType keyword) {
    if (lexer.getTokenType() != keyword) return null;

    CompositeElement list = Factory.createCompositeElement(elementType);
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

  public CompositeElement parseParameterText(char[] buffer) {
    Lexer originalLexer = new JavaLexer(myContext.getLanguageLevel());
    FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, 0, buffer.length);
    ASTNode first = parseParameter(lexer, true);
    if (first == null || first.getElementType() != PARAMETER) return null;
    if (lexer.getTokenType() != null) return null;

    ParseUtil.insertMissingTokens((CompositeElement)first, originalLexer, 0, buffer.length, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (CompositeElement)first;
  }

  public TreeElement parseParameter(Lexer lexer, boolean allowEllipsis) {
    final LexerPosition pos = lexer.getCurrentPosition();

    CompositeElement modifierList = parseModifierList(lexer);

    CompositeElement type = allowEllipsis ? parseTypeWithEllipsis(lexer) : parseType(lexer);
    if (type == null){
      lexer.restore(pos);
      return null;
    }

    CompositeElement param = Factory.createCompositeElement(PARAMETER);
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
