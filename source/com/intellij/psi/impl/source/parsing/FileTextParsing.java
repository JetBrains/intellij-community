package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.roots.JavaProjectExtension;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class FileTextParsing extends Parsing {
  public FileTextParsing(JavaParsingContext context) {
    super(context);
  }

  public static TreeElement parseFileText(PsiManager manager, Lexer lexer, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    return parseFileText(manager, lexer, buffer, startOffset, endOffset, false, table);
  }

  private static final TokenSet IMPORT_LIST_STOPPER_BIT_SET = TokenSet.create(CLASS_KEYWORD, INTERFACE_KEYWORD, ENUM_KEYWORD, AT);

  public static TreeElement parseFileText(PsiManager manager, @NotNull Lexer lexer, CharSequence buffer, int startOffset, int endOffset, boolean skipHeader, CharTable table) {
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, startOffset, endOffset,0);
    final FileElement dummyRoot = new JavaDummyHolder(manager, null, table).getTreeElement();
    JavaParsingContext context = new JavaParsingContext(dummyRoot.getCharTable(),
                                                        JavaProjectExtension.getInstance(manager.getProject()).getLanguageLevel());

    if (!skipHeader){
      TreeElement packageStatement = (TreeElement)context.getFileTextParsing().parsePackageStatement(filterLexer);
      if (packageStatement != null) {
        TreeUtil.addChildren(dummyRoot, packageStatement);
      }

      final TreeElement importList = (TreeElement)context.getFileTextParsing().parseImportList(filterLexer);
      TreeUtil.addChildren(dummyRoot, importList);
    }

    CompositeElement invalidElementsGroup = null;
    while (true) {
      if (filterLexer.getTokenType() == null) break;

      if (filterLexer.getTokenType() == ElementType.SEMICOLON){
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(filterLexer, dummyRoot.getCharTable()));
        filterLexer.advance();
        invalidElementsGroup = null;
        continue;
      }

      TreeElement first = context.getDeclarationParsing().parseDeclaration(filterLexer, DeclarationParsing.Context.FILE_CONTEXT);
      if (first != null) {
        TreeUtil.addChildren(dummyRoot, first);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement(JavaErrorMessages.message("expected.class.or.interface"));
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }
      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, context.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return dummyRoot.getFirstChildNode();
  }

  public ASTNode parseImportList(Lexer lexer) {
    CompositeElement importList = Factory.createCompositeElement(IMPORT_LIST);
    if (lexer.getTokenType() == IMPORT_KEYWORD) {
      int startPos = lexer.getTokenStart();
      int lastPos = lexer.getTokenEnd();
      boolean prevImportKeyword = true;
      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == IMPORT_KEYWORD) {
          prevImportKeyword = true;
        }
        else if (prevImportKeyword && tokenType == STATIC_KEYWORD) {
          prevImportKeyword = false;
        }
        else {
          prevImportKeyword = StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType);
          if (tokenType == null || IMPORT_LIST_STOPPER_BIT_SET.contains(tokenType) || MODIFIER_BIT_SET.contains(tokenType)) break;
        }
        lastPos = lexer.getTokenEnd();
        lexer.advance();
      }
      return Factory.createLeafElement(IMPORT_LIST, lexer.getBufferSequence(), startPos, lastPos, myContext.getCharTable());
    }

    return importList;
  }

  public ASTNode parsePackageStatement(Lexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();
    CompositeElement packageStatement = Factory.createCompositeElement(PACKAGE_STATEMENT);

    if (lexer.getTokenType() != PACKAGE_KEYWORD) {
      FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
      TreeUtil.addChildren(packageStatement, myContext.getDeclarationParsing().parseAnnotationList(filterLexer));
      if (lexer.getTokenType() != PACKAGE_KEYWORD) {
        lexer.restore(startPos);
        return null;
      }
    }

    TreeUtil.addChildren(packageStatement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement packageReference = parseJavaCodeReference(lexer, true, false);
    if (packageReference == null) {
      lexer.restore(startPos);
      return null;
    }
    TreeUtil.addChildren(packageStatement, packageReference);
    if (lexer.getTokenType() == SEMICOLON) {
      TreeUtil.addChildren(packageStatement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    } else {
      TreeUtil.addChildren(packageStatement, Factory.createErrorElement(JavaErrorMessages.message("expected.semicolon")));
    }
    return packageStatement;
  }
}



