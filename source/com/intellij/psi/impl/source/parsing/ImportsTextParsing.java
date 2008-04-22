package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ImportsTextParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ImportsTextParsing");
  private static final TokenSet IMPORT_LIST_STOPPER_BIT_SET = TokenSet.create(CLASS_KEYWORD, INTERFACE_KEYWORD, ENUM_KEYWORD, AT);

  public ImportsTextParsing(JavaParsingContext context) {
    super(context);
  }

  public void parseImportStatements(final Lexer filterLexer, final CompositeElement parentNode) {
    CompositeElement invalidElementsGroup = null;
    while(true){
      IElementType tt = filterLexer.getTokenType();
      if (tt == null || IMPORT_LIST_STOPPER_BIT_SET.contains(tt) || MODIFIER_BIT_SET.contains(tt)) {
        break;
      }

      TreeElement element = (TreeElement)parseImportStatement(filterLexer);
      if (element != null){
        TreeUtil.addChildren(parentNode, element);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("unexpected.token"));
        TreeUtil.addChildren(parentNode, invalidElementsGroup);
      }

      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }
  }

  @Nullable
  private ASTNode parseImportStatement(Lexer lexer) {
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
    final TreeElement refParameterList = refElement.getLastChildNode();
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

  public static CompositeElement convertToImportStaticReference(CompositeElement refElement) {
    final CompositeElement importStaticReference = ASTFactory.composite(IMPORT_STATIC_REFERENCE);
    final CompositeElement referenceParameterList = (CompositeElement)refElement.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    TreeUtil.addChildren(importStaticReference, refElement.getFirstChildNode());
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
