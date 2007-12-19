package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.JavaDummyHolder;
import static com.intellij.psi.impl.source.parsing.DeclarationParsing.Context.ANNOTATION_INTERFACE_CONTEXT;
import static com.intellij.psi.impl.source.parsing.DeclarationParsing.Context.CLASS_CONTEXT;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

/**
 *
 */
public class ClassBodyParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ClassBodyParsing");
  public static final int CLASS = 0;
  public static final int ANNOTATION = 1;
  public static final int ENUM = 2;

  public ClassBodyParsing(JavaParsingContext context) {
    super(context);
  }


  public TreeElement parseClassBodyText(Lexer lexer,
                                        CharSequence buffer,
                                        int startOffset,
                                        int endOffset,
                                        int lexerState,
                                        int context,
                                        PsiManager manager){
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (lexerState < 0) filterLexer.start(buffer, startOffset, endOffset,0);
    else filterLexer.start(buffer, startOffset, endOffset, lexerState);

    CharTable table = myContext.getCharTable();
    final FileElement dummyRoot = new JavaDummyHolder(manager, null, table).getTreeElement();
    parseClassBody(dummyRoot, filterLexer, context);
    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, lexerState, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (TreeElement)dummyRoot.getFirstChildNode();
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
        TreeUtil.addChildren(dummyRoot, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      }

      if (lexer.getTokenType() == COMMA) {
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else if (lexer.getTokenType() != null && lexer.getTokenType() != SEMICOLON) {
        TreeUtil.addChildren(dummyRoot, Factory.createErrorElement(JavaErrorMessages.message("expected.comma.or.semicolon")));
        return;
      }
    }
  }

  private void parseClassBodyDeclarations(int context, Lexer filterLexer, CompositeElement dummyRoot) {
    CompositeElement invalidElementsGroup = null;
    final DeclarationParsing.Context declarationParsingContext = calcDeclarationContext(context);

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
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      CompositeElement ref = parseJavaCodeReference(filterLexer, true, true);
      if (ref != null){
        TreeUtil.addChildren(invalidElementsGroup, ref);
        continue;
      }

      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }
  }

  private static DeclarationParsing.Context calcDeclarationContext(int context) {
    DeclarationParsing.Context declarationParsingContext = CLASS_CONTEXT;
    switch(context) {
      default:
        LOG.assertTrue(false);
        break;
      case CLASS:
      case ENUM:
        declarationParsingContext = CLASS_CONTEXT;
        break;
      case ANNOTATION:
        declarationParsingContext = ANNOTATION_INTERFACE_CONTEXT;
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


