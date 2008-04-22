package com.intellij.psi.impl.source.parsing;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static TreeElement parseFileText(PsiManager manager, @NotNull Lexer lexer, CharSequence buffer, int startOffset, int endOffset, boolean skipHeader, CharTable table) {
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, startOffset, endOffset,0);
    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, table).getTreeElement();
    JavaParsingContext context = new JavaParsingContext(dummyRoot.getCharTable(),
                                                        LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel());

    if (!skipHeader){
      TreeElement packageStatement = (TreeElement)context.getFileTextParsing().parsePackageStatement(filterLexer);
      if (packageStatement != null) {
        TreeUtil.addChildren(dummyRoot, packageStatement);
      }

      final TreeElement importList = (TreeElement)parseImportList(filterLexer, context);
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

  public static ASTNode parseImportList(Lexer lexer, final JavaParsingContext context) {
    CompositeElement importList = ASTFactory.composite(IMPORT_LIST);

    if (lexer.getTokenType() == IMPORT_KEYWORD) {
      context.getImportsTextParsing().parseImportStatements(lexer, importList);
    }

    return importList;
  }

  @Nullable
  private ASTNode parsePackageStatement(Lexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();
    CompositeElement packageStatement = ASTFactory.composite(PACKAGE_STATEMENT);

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



