package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;

/**
 *
 */
public class ClassBodyParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ClassBodyParsing");
  public static final int CLASS = 0;
  public static final int ANNOTATION = 1;
  public static final int ENUM = 2;

  public ClassBodyParsing(ParsingContext context) {
    super(context);
  }


  public static TreeElement parseClassBodyText(Lexer lexer,
                                               char[] buffer,
                                               int startOffset,
                                               int endOffset,
                                               int lexerState,
                                               int context,
                                               CharTable table, PsiManager manager){
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (lexerState < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, lexerState);

    final FileElement dummyRoot = new DummyHolder(manager, null, table).getTreeElement();
    ParsingContext parsingContext = new ParsingContext(table);
    parsingContext.getClassBodyParsing().parseClassBody(dummyRoot, filterLexer, context);
    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, lexerState, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, parsingContext);
    return dummyRoot.firstChild;
  }

  private void parseEnumConstants(Lexer lexer, CompositeElement dummyRoot) {
    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == JavaTokenType.SEMICOLON) {
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
        return;
      }

      final TreeElement enumConstant = myContext.getDeclarationParsing().parseEnumConstant(lexer);
      if (enumConstant != null) {
        TreeUtil.addChildren(dummyRoot, enumConstant);
      }
      else {
        TreeUtil.addChildren(dummyRoot, Factory.createErrorElement("Identifier expected"));
      }

      if (lexer.getTokenType() == COMMA) {
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else if (lexer.getTokenType() != null && lexer.getTokenType() != SEMICOLON) {
        TreeUtil.addChildren(dummyRoot, Factory.createErrorElement("',' or ';' expected"));
        return;
      }
    }
  }

  private void parseClassBodyDeclarations(int context, Lexer filterLexer, CompositeElement dummyRoot) {
    CompositeElement invalidElementsGroup = null;
    final int declarationParsingContext = calcDeclarationContext(context);

    while(true){
      IElementType tokenType = filterLexer.getTokenType();
      if (tokenType == null) break;

      if (tokenType == SEMICOLON){
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
        filterLexer.advance();
        invalidElementsGroup = null;
        continue;
      }

      TreeElement declaration = myContext.getDeclarationParsing().parseDeclaration(filterLexer,
                                                                    declarationParsingContext);
      if (declaration != null){
        TreeUtil.addChildren(dummyRoot, declaration);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement("Unexpected token");
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      CompositeElement ref = parseJavaCodeReference(filterLexer, true);
      if (ref != null){
        TreeUtil.addChildren(invalidElementsGroup, ref);
        continue;
      }

      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }
  }

  private static int calcDeclarationContext(int context) {
    int declarationParsingContext = DeclarationParsing.CLASS_CONTEXT;
    switch(context) {
      default:
        LOG.assertTrue(false);
        break;
      case CLASS:
      case ENUM:
        declarationParsingContext = DeclarationParsing.CLASS_CONTEXT;
        break;
      case ANNOTATION:
        declarationParsingContext = DeclarationParsing.ANNOTATION_INTERFACE_CONTEXT;
        break;
    }
    return declarationParsingContext;
  }

  public void parseClassBody(CompositeElement root, Lexer lexer, int context) {
    if (context == ENUM) {
      parseEnumConstants (lexer, root);
    }
    parseClassBodyDeclarations(context, lexer, root);
  }
}


