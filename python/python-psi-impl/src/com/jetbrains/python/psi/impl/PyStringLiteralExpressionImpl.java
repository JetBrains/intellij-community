// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.PsiReferenceService.Hints;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression, PsiLiteralValue, ContributedReferenceHost {

  @Nullable private volatile String myStringValue;
  @Nullable private volatile List<TextRange> myValueTextRanges;
  @Nullable private volatile List<Pair<TextRange, String>> myDecodedFragments;

  public PyStringLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStringLiteralExpression(this);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myStringValue = null;
    myValueTextRanges = null;
    myDecodedFragments = null;
  }

  @Override
  @NotNull
  public List<TextRange> getStringValueTextRanges() {
    List<TextRange> result = myValueTextRanges;
    if (result == null) {
      final int elementStart = getTextRange().getStartOffset();
      final List<TextRange> ranges = StreamEx.of(getStringElements())
        .map(node -> {
          final int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
          return node.getContentRange().shiftRight(nodeRelativeOffset);
        })
        .toList();
      myValueTextRanges = result = Collections.unmodifiableList(ranges);
    }
    return result;
  }

  @Override
  @NotNull
  public List<Pair<TextRange, String>> getDecodedFragments() {
    final int elementStart = getTextRange().getStartOffset();
    List<Pair<TextRange, String>> result = myDecodedFragments;
    if (result == null) {
      final List<Pair<TextRange, String>> combined = StreamEx.of(getStringElements())
        .flatMap(node -> StreamEx.of(node.getDecodedFragments())
          .map(pair -> {
            final int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
            return Pair.create(pair.getFirst().shiftRight(nodeRelativeOffset), pair.getSecond());
          }))
        .toList();
      myDecodedFragments = result = Collections.unmodifiableList(combined);
    }
    return result;
  }

  @Override
  public boolean isDocString() {
    final List<ASTNode> stringNodes = getStringNodes();
    return stringNodes.size() == 1 && stringNodes.get(0).getElementType() == PyTokenTypes.DOCSTRING;
  }

  @Override
  public boolean isInterpolated() {
    return StreamEx.of(getStringElements())
      .select(PyFormattedStringElement.class)
      .anyMatch(element -> !element.getFragments().isEmpty());
  }

  @Override
  @NotNull
  public List<ASTNode> getStringNodes() {
    final TokenSet stringNodeTypes = TokenSet.orSet(PyTokenTypes.STRING_NODES, TokenSet.create(PyElementTypes.FSTRING_NODE));
    return Arrays.asList(getNode().getChildren(stringNodeTypes));
  }

  @NotNull
  @Override
  public List<PyStringElement> getStringElements() {
    return StreamEx.of(getStringNodes())
      .map(ASTNode::getPsi)
      .select(PyStringElement.class)
      .toList();
  }

  @NotNull
  @Override
  public String getStringValue() {
    //ASTNode child = getNode().getFirstChildNode();
    //assert child != null;
    String result = myStringValue;
    if (result == null) {
      final StringBuilder out = new StringBuilder();
      for (Pair<TextRange, String> fragment : getDecodedFragments()) {
        out.append(fragment.getSecond());
      }
      myStringValue = result = out.toString();
    }
    return result;
  }

  @Nullable
  @Override
  public Object getValue() {
    return getStringValue();
  }

  @Override
  public TextRange getStringValueTextRange() {
    List<TextRange> allRanges = getStringValueTextRanges();
    if (allRanges.size() == 1) {
      return allRanges.get(0);
    }
    if (allRanges.size() > 1) {
      return allRanges.get(0).union(allRanges.get(allRanges.size() - 1));
    }
    return new TextRange(0, getTextLength());
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getStringValue();
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyFile file = PyUtil.as(FileContextUtil.getContextFile(this), PyFile.class);
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(file == null ? this : file);
    final LanguageLevel languageLevel = file == null ? LanguageLevel.forElement(this) : file.getLanguageLevel();

    final ASTNode firstNode = ContainerUtil.getFirstItem(getStringNodes());
    if (firstNode != null) {
      if (firstNode.getElementType() == PyElementTypes.FSTRING_NODE) {
        // f-strings can't have "b" prefix so they are always unicode
        return builtinCache.getUnicodeType(languageLevel);
      }
      else if (firstNode.getElementType() == PyTokenTypes.DOCSTRING) {
        return builtinCache.getStrType();
      }
      else if (((PyStringElement)firstNode).isBytes()) {
        return builtinCache.getBytesType(languageLevel);
      }

      final IElementType type = PythonHighlightingLexer.convertStringType(firstNode.getElementType(),
                                                                          firstNode.getText(),
                                                                          languageLevel,
                                                                          (file != null &&
                                                                           file.hasImportFromFuture(FutureFeature.UNICODE_LITERALS)));
      if (PyTokenTypes.UNICODE_NODES.contains(type)) {
        return builtinCache.getUnicodeType(languageLevel);
      }
    }
    return builtinCache.getStrType();
  }

  @Override
  public final PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, Hints.NO_HINTS);
  }

  @Override
  public PsiReference getReference() {
    return ArrayUtil.getFirstElement(getReferences());
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return getStringValue();
      }

      @Nullable
      @Override
      public String getLocationString() {
        String packageForFile = PyElementPresentation.getPackageForFile(getContainingFile());
        return packageForFile != null ? String.format("(%s)", packageForFile) : null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Nodes.Variable;
      }
    };
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return ElementManipulators.handleContentChange(this, text);
  }

  @Override
  @NotNull
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new StringLiteralTextEscaper(this);
  }

  private static class StringLiteralTextEscaper extends LiteralTextEscaper<PyStringLiteralExpression> {
    private final PyStringLiteralExpressionImpl myHost;

    protected StringLiteralTextEscaper(@NotNull PyStringLiteralExpressionImpl host) {
      super(host);
      myHost = host;
    }

    @Override
    public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull final StringBuilder outChars) {
      for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
        final TextRange encodedTextRange = fragment.getFirst();
        final TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
        if (intersection != null && !intersection.isEmpty()) {
          final String value = fragment.getSecond();
          final String intersectedValue;
          if (value.codePointCount(0, value.length()) == 1 || value.length() == intersection.getLength()) {
            intersectedValue = value;
          }
          else {
            final int start = Math.max(0, rangeInsideHost.getStartOffset() - encodedTextRange.getStartOffset());
            final int end = Math.min(value.length(), start + intersection.getLength());
            intersectedValue = value.substring(start, end);
          }
          outChars.append(intersectedValue);
        }
      }
      return true;
    }

    @Override
    public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
      int offset = 0; // running offset in the decoded fragment
      int endOffset = -1;
      for (Pair<TextRange, String> fragment : myHost.getDecodedFragments()) {
        final TextRange encodedTextRange = fragment.getFirst();
        final TextRange intersection = encodedTextRange.intersection(rangeInsideHost);
        if (intersection != null && !intersection.isEmpty()) {
          final String value = fragment.getSecond();
          final int valueLength = value.length();
          final int intersectionLength = intersection.getLength();
          if (valueLength == 0) {
            return -1;
          }
          // A long unicode escape of form \U01234567 can be decoded into a surrogate pair
          else if (value.codePointCount(0, valueLength) == 1) {
            if (offset == offsetInDecoded) {
              return intersection.getStartOffset();
            }
            offset += valueLength;
          }
          else {
            // Literal fragment without escapes: it's safe to use intersection length instead of value length
            if (offset + intersectionLength >= offsetInDecoded) {
              final int delta = offsetInDecoded - offset;
              return intersection.getStartOffset() + delta;
            }
            offset += intersectionLength;
          }
          endOffset = intersection.getEndOffset();
        }
      }
      // XXX: According to the real use of getOffsetInHost() it should return the correct host offset for the offset in decoded at the
      // end of the range inside host, not -1
      if (offset == offsetInDecoded) {
        return endOffset;
      }
      return -1;
    }

    @Override
    public boolean isOneLine() {
      return true;
    }

    @NotNull
    @Override
    public TextRange getRelevantTextRange() {
      return myHost.getStringValueTextRange();
    }
  }

  @Override
  public int valueOffsetToTextOffset(int valueOffset) {
    return createLiteralTextEscaper().getOffsetInHost(valueOffset, getStringValueTextRange());
  }


}
