package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;

/**
 *
 */
public class ImportsTextParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ImportsTextParsing");

  public ImportsTextParsing(JavaParsingContext context) {
    super(context);
  }

  /**
   * @stereotype chameleon transforming
   */
  public TreeElement parseImportsText(PsiManager manager, Lexer lexer, CharSequence buffer, int startOffset, int endOffset, int state) {
    if (lexer == null){
      lexer = new JavaLexer(LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());
    }
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset,0);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement invalidElementsGroup = null;
    while(filterLexer.getTokenType() != null){
      TreeElement element = (TreeElement)parseImportStatement(filterLexer);
      if (element != null){
        TreeUtil.addChildren(dummyRoot, element);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }
      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (TreeElement)dummyRoot.getFirstChildNode();
  }

  private ASTNode parseImportStatement(FilterLexer lexer) {
    if (lexer.getTokenType() != IMPORT_KEYWORD) return null;

    final TreeElement importToken = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();
    final CompositeElement statement;
    final boolean isStatic;
    if (lexer.getTokenType() != STATIC_KEYWORD) {
      statement = ASTFactory.composite(IMPORT_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      isStatic = false;
    }
    else {
      statement = ASTFactory.composite(IMPORT_STATIC_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      isStatic = true;
    }

    if (lexer.getTokenType() != IDENTIFIER){
      TreeUtil.addChildren(statement, Factory.createErrorElement(JavaErrorMessages.message("expected.identifier")));
      return statement;
    }

    CompositeElement refElement = parseJavaCodeReference(lexer, true, false);
    final TreeElement refParameterList = (TreeElement)refElement.getLastChildNode();
    if (refParameterList.getTreePrev().getElementType() == ERROR_ELEMENT){
      final ASTNode qualifier = refElement.findChildByRole(ChildRole.QUALIFIER);
      LOG.assertTrue(qualifier != null);
      TreeUtil.remove(refParameterList.getTreePrev());
      TreeUtil.remove(refParameterList);
      TreeUtil.addChildren(statement, (TreeElement)qualifier);
      if (lexer.getTokenType() == ASTERISK){
        TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else{
        TreeUtil.addChildren(statement,
                             Factory.createErrorElement(JavaErrorMessages.message("import.statement.identifier.or.asterisk.expected.")));
        return statement;
      }
    }
    else{
      if (isStatic) {
        // convert JAVA_CODE_REFERENCE into IMPORT_STATIC_REFERENCE
        refElement = convertToImportStaticReference(refElement);
      }
      TreeUtil.addChildren(statement, refElement);
    }

    if (lexer.getTokenType() == SEMICOLON){
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      TreeUtil.addChildren(statement, Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
    }

    return statement;
  }

  public CompositeElement convertToImportStaticReference(CompositeElement refElement) {
    final CompositeElement importStaticReference = ASTFactory.composite(IMPORT_STATIC_REFERENCE);
    final CompositeElement referenceParameterList = (CompositeElement)refElement.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    TreeUtil.addChildren(importStaticReference, (TreeElement)refElement.getFirstChildNode());
    if (referenceParameterList != null) {
      if (referenceParameterList.getFirstChildNode() == null) {
        TreeUtil.remove(referenceParameterList);
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        TreeUtil.replaceWithList(referenceParameterList, errorElement);
        TreeUtil.addChildren(errorElement, referenceParameterList);
      }
    }
    refElement = importStaticReference;
    return refElement;
  }
}
