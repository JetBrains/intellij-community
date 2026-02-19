package org.intellij.plugins.markdown.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ParsingDiagnostics;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.plugins.markdown.lang.lexer.MarkdownMergingLexer;
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourUtil;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.intellij.plugins.markdown.lang.parser.PsiBuilderFillingVisitor;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MarkdownLazyElementType extends ILazyParseableElementType {
  private static final Logger LOG = Logger.getInstance(MarkdownLazyElementType.class);

  public MarkdownLazyElementType(@NotNull @NonNls String debugName) {
    super(debugName, MarkdownLanguage.INSTANCE);
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    final Project project = psi.getProject();
    final Lexer lexer = new MarkdownMergingLexer();
    final CharSequence chars = new StringUtil.BombedCharSequence(chameleon.getChars()) {
      @Override
      protected void checkCanceled() {
        ProgressManager.checkCanceled();
      }
    };

    final var file = psi.getContainingFile();
    Objects.requireNonNull(file, () -> "Expected a non-null containing file for " + psi);
    final var flavour = obtainFlavour(file);
    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, getLanguage(), chars);

    var startTime = System.nanoTime();
    final var parser = new MarkdownParser(flavour, true);
    final var nodeType = MarkdownElementType.markdownType(chameleon.getElementType());
    final var node = parser.parseInline(nodeType, chars, 0, chars.length());

    PsiBuilder.Marker rootMarker = builder.mark();

    //Flatten type is used to solve problem with trailing whitespaces
    new PsiBuilderFillingVisitor(builder, false).visitNode(node);
    assert builder.eof();

    rootMarker.done(this);

    final var tree = builder.getTreeBuilt();
    final var actualElement = tree.getFirstChildNode().getFirstChildNode();

    ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);

    return actualElement;
  }

  private static @NotNull MarkdownFlavourDescriptor obtainFlavour(@NotNull PsiFile file) {
    if (file instanceof MarkdownFile markdownFile) {
      return markdownFile.getFlavour();
    }
    final var flavour = file.getUserData(MarkdownParserManager.FLAVOUR_DESCRIPTION);
    if (flavour != null) {
      return flavour;
    }
    final var defaultFlavour = MarkdownFlavourUtil.obtainDefaultMarkdownFlavour();
    LOG.error("Markdown flavour was not set for " + file + ". Using default flavour as a fallback: " + defaultFlavour);
    return defaultFlavour;
  }
}
