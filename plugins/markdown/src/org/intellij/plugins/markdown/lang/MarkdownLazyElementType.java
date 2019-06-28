package org.intellij.plugins.markdown.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.plugins.markdown.lang.lexer.MarkdownMergingLexer;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.intellij.plugins.markdown.lang.parser.PsiBuilderFillingVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MarkdownLazyElementType extends ILazyParseableElementType {
  public MarkdownLazyElementType(@NotNull @NonNls String debugName) {
    super(debugName, MarkdownLanguage.INSTANCE);
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    final Project project = psi.getProject();
    final Lexer lexer = new MarkdownMergingLexer();
    final CharSequence chars = chameleon.getChars();

    final org.intellij.markdown.ast.ASTNode node = new MarkdownParser(MarkdownParserManager.FLAVOUR)
      .parseInline(MarkdownElementType.markdownType(chameleon.getElementType()), chars, 0, chars.length());

    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, getLanguage(), chars);
    assert builder.getCurrentOffset() == 0;
    new PsiBuilderFillingVisitor(builder).visitNode(node);
    assert builder.eof();

    return builder.getTreeBuilt().getFirstChildNode();
  }
}
