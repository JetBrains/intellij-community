package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;

/**
 *
 */
public class ImportsTextParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ImportsTextParsing");

  public ImportsTextParsing(ParsingContext context) {
    super(context);
  }

  /**
   * @stereotype chameleon transforming
   */
  public TreeElement parseImportsText(PsiManager manager, Lexer lexer, char[] buffer, int startOffset, int endOffset, int state) {
    if (lexer == null){
      lexer = new JavaLexer(manager.getEffectiveLanguageLevel());
    }
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement invalidElementsGroup = null;
    while(filterLexer.getTokenType() != null){
      TreeElement element = parseImportStatement(filterLexer);
      if (element != null){
        TreeUtil.addChildren(dummyRoot, element);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement("Unexpected token");
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }
      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return dummyRoot.firstChild;
  }

  private CompositeElement parseImportStatement(FilterLexer lexer) {
    if (lexer.getTokenType() != IMPORT_KEYWORD) return null;

    final TreeElement importToken = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();
    final CompositeElement statement;
    final boolean isStatic;
    if (lexer.getTokenType() != STATIC_KEYWORD) {
      statement = Factory.createCompositeElement(IMPORT_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      isStatic = false;
    }
    else {
      statement = Factory.createCompositeElement(IMPORT_STATIC_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      isStatic = true;
    }

    if (lexer.getTokenType() != IDENTIFIER){
      TreeUtil.addChildren(statement, Factory.createErrorElement("Identifier expected"));
      return statement;
    }

    CompositeElement refElement = parseJavaCodeReference(lexer, true);
    if (refElement.lastChild.getElementType() == ERROR_ELEMENT){
      final TreeElement qualifier = refElement.findChildByRole(ChildRole.QUALIFIER);
      LOG.assertTrue(qualifier != null);
      TreeUtil.remove(refElement.lastChild);
      TreeUtil.addChildren(statement, qualifier);
      if (lexer.getTokenType() == ASTERISK){
        TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else{
        TreeUtil.addChildren(statement, Factory.createErrorElement("Identifier or '*' expected"));
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
      TreeUtil.addChildren(statement, Factory.createErrorElement("';' expected"));
    }

    return statement;
  }

  public CompositeElement convertToImportStaticReference(CompositeElement refElement) {
    final CompositeElement importStaticReference = Factory.createCompositeElement(IMPORT_STATIC_REFERENCE);
    final CompositeElement referenceParameterList = (CompositeElement)refElement.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    TreeUtil.addChildren(importStaticReference, refElement.firstChild);
    if (referenceParameterList != null) {
      if (referenceParameterList.firstChild == null) {
        TreeUtil.remove(referenceParameterList);
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement("Unexpected token");
        TreeUtil.replace(referenceParameterList, errorElement);
        TreeUtil.addChildren(errorElement, referenceParameterList);
      }
    }
    refElement = importStaticReference;
    return refElement;
  }
}
