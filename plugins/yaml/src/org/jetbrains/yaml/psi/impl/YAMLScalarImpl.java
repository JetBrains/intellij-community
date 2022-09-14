// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class YAMLScalarImpl extends YAMLValueImpl implements YAMLScalar {
  protected static final int MAX_SCALAR_LENGTH_PREDEFINED = 60;

  public YAMLScalarImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public abstract List<TextRange> getContentRanges();
  
  public abstract @NotNull YamlScalarTextEvaluator getTextEvaluator();

  protected List<Pair<TextRange, String>> getDecodeReplacements(@NotNull CharSequence input) {
    return Collections.emptyList();
  }

  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public final String getTextValue() {
    return getTextEvaluator().getTextValue(null);
  }

  @NotNull
  public final String getTextValue(@Nullable TextRange rangeInHost) {
    return getTextEvaluator().getTextValue(rangeInHost);
  }

  @Override
  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    return references.length == 1 ? references[0] : null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return ElementManipulators.handleContentChange(this, text);
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new MyLiteralTextEscaper(this);
  }

  @NotNull
  static String processReplacements(@NotNull CharSequence input,
                                    @NotNull List<? extends Pair<TextRange, String>> replacements) throws IndexOutOfBoundsException {
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
    MyLiteralTextEscaper(YAMLScalarImpl scalar) {
      super(scalar);
    }

    private String text;
    private List<TextRange> contentRanges;
    
    @Override
    public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
      text = myHost.getText();
      contentRanges = myHost.getContentRanges();
      boolean decoded = false;
      for (TextRange range : contentRanges) {
        TextRange intersection = range.intersection(rangeInsideHost);
        if (intersection == null) continue;
        decoded = true;
        String substring = intersection.substring(text);
        outChars.append(processReplacements(substring, myHost.getDecodeReplacements(substring)));
      }
      return decoded;
    }

    @Override
    public @NotNull TextRange getRelevantTextRange() {
      if (contentRanges == null) {
        contentRanges = myHost.getContentRanges();
      }
      if (contentRanges.isEmpty()) return TextRange.EMPTY_RANGE;
      return TextRange.create(contentRanges.get(0).getStartOffset(), contentRanges.get(contentRanges.size() - 1).getEndOffset());
    }

    @Override
    public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {

      int currentOffsetInDecoded = 0;

      TextRange last = null;
      for (int i = 0; i < contentRanges.size(); i++) {
        final TextRange range = rangeInsideHost.intersection(contentRanges.get(i));
        if (range == null) continue;
        last = range;

        String curString = range.subSequence(text).toString();

        final List<Pair<TextRange, String>> replacementsForThisLine = myHost.getDecodeReplacements(curString);
        int encodedOffsetInCurrentLine = 0;
        for (Pair<TextRange, String> replacement : replacementsForThisLine) {
          final int deltaLength = replacement.getFirst().getStartOffset() - encodedOffsetInCurrentLine;
          int currentOffsetBeforeReplacement = currentOffsetInDecoded + deltaLength;
          if (currentOffsetBeforeReplacement > offsetInDecoded) {
            return range.getStartOffset() + encodedOffsetInCurrentLine + (offsetInDecoded - currentOffsetInDecoded);
          }
          else if (currentOffsetBeforeReplacement == offsetInDecoded && !replacement.getSecond().isEmpty()) {
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

      return last != null ? last.getEndOffset() : -1;
    }

    @Override
    public boolean isOneLine() {
      return !myHost.isMultiline();
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

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public @NotNull String getPresentableText() {
        return StringUtil.shortenTextWithEllipsis(getTextValue(), 20, 0, true);
      }

      @Override
      public @NotNull String getLocationString() {
        return getContainingFile().getName();
      }

      @Override
      public @NotNull Icon getIcon(boolean unused) {
        return AllIcons.Nodes.Variable;
      }
    };
  }
}
