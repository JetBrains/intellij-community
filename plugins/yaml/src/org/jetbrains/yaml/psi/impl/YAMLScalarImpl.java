package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.Collections;
import java.util.List;

public abstract class YAMLScalarImpl extends YAMLValueImpl implements YAMLScalar {
  protected static final int MAX_SCALAR_LENGTH_PREDEFINED = 60;

  public YAMLScalarImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public abstract List<TextRange> getContentRanges();

  @NotNull
  protected abstract String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore);

  protected List<Pair<TextRange, String>> getDecodeReplacements(@NotNull CharSequence input) {
    return Collections.emptyList();
  }

  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    throw new IllegalArgumentException("Not implemented");
  }

  @NotNull
  @Override
  public String getTextValue() {
    final String text = getText();
    final List<TextRange> contentRanges = getContentRanges();

    final StringBuilder builder = new StringBuilder();

    for (int i = 0; i < contentRanges.size(); i++) {
      final TextRange range = contentRanges.get(i);

      final CharSequence curString = range.subSequence(text);
      builder.append(curString);

      if (i + 1 != contentRanges.size()) {
        builder.append(getRangesJoiner(text, contentRanges, i));
      }
    }
    return processReplacements(builder, getDecodeReplacements(builder));
  }


  @Override
  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    return references.length == 1 ? references[0] : null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return ElementManipulators.getManipulator(this).handleContentChange(this, text);
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new MyLiteralTextEscaper(this);
  }

  @NotNull
  static String processReplacements(@NotNull CharSequence input,
                                    @NotNull List<Pair<TextRange, String>> replacements) throws IndexOutOfBoundsException {
    StringBuilder result = new StringBuilder();
    int currentOffset = 0;
    for (Pair<TextRange, String> replacement : replacements) {
      result.append(input.subSequence(currentOffset, replacement.getFirst().getStartOffset()));
      result.append(replacement.getSecond());
      currentOffset = replacement.getFirst().getEndOffset();
    }
    result.append(input.subSequence(currentOffset, input.length()));
    return result.toString();
  }

  protected static boolean isSurroundedByNoSpace(CharSequence text, int pos) {
    return (pos - 1 < 0 || !YAMLGrammarCharUtil.isSpaceLike(text.charAt(pos - 1)))
           && (pos + 1 >= text.length() || !YAMLGrammarCharUtil.isSpaceLike(text.charAt(pos + 1)));
  }

  @Nullable
  protected final ASTNode getFirstContentNode() {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null && (
      node.getElementType() == YAMLTokenTypes.TAG || YAMLElementTypes.BLANK_ELEMENTS.contains(node.getElementType()))) {
      node = node.getTreeNext();
    }
    return node;
  }

  private static class MyLiteralTextEscaper extends LiteralTextEscaper<YAMLScalarImpl> {
    public MyLiteralTextEscaper(YAMLScalarImpl scalar) {
      super(scalar);
    }

    @Override
    public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
      outChars.append(myHost.getTextValue());
      return true;
    }

    @Override
    public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
      final String text = myHost.getText();
      final List<TextRange> contentRanges = myHost.getContentRanges();

      int currentOffsetInDecoded = 0;

      for (int i = 0; i < contentRanges.size(); i++) {
        final TextRange range = contentRanges.get(i);

        String curString = range.subSequence(text).toString();

        if (i + 1 != contentRanges.size()) {
          final String joiner = myHost.getRangesJoiner(text, contentRanges, i);
          curString += joiner;
        }

        final List<Pair<TextRange, String>> replacementsForThisLine = myHost.getDecodeReplacements(curString);
        int encodedOffsetInCurrentLine = 0;
        for (Pair<TextRange, String> replacement : replacementsForThisLine) {
          final int deltaLength = replacement.getFirst().getStartOffset() - encodedOffsetInCurrentLine;
          if (currentOffsetInDecoded + deltaLength >= offsetInDecoded) {
            return range.getStartOffset() + encodedOffsetInCurrentLine + (offsetInDecoded - currentOffsetInDecoded);
          }
          currentOffsetInDecoded += deltaLength + replacement.getSecond().length();
          encodedOffsetInCurrentLine += deltaLength + replacement.getFirst().getLength();
        }

        final int deltaLength = curString.length() - encodedOffsetInCurrentLine;
        if (currentOffsetInDecoded + deltaLength > offsetInDecoded) {
          return range.getStartOffset() + encodedOffsetInCurrentLine + (offsetInDecoded - currentOffsetInDecoded);
        }
        currentOffsetInDecoded += deltaLength;
      }

      //noinspection ConstantConditions
      return ContainerUtil.getLastItem(contentRanges, rangeInsideHost).getEndOffset();
    }

    @Override
    public boolean isOneLine() {
      return myHost.isMultiline();
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitScalar(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
