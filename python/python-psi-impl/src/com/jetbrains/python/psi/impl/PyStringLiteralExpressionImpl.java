// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiLiteralValue;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService.Hints;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression, PsiLiteralValue, ContributedReferenceHost {

  private volatile @Nullable String myStringValue;
  private volatile @Nullable List<TextRange> myValueTextRanges;
  private volatile @Nullable List<Pair<TextRange, String>> myDecodedFragments;

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
  public @NotNull List<TextRange> getStringValueTextRanges() {
    List<TextRange> result = myValueTextRanges;
    if (result == null) {
      myValueTextRanges = result = PyStringLiteralExpression.super.getStringValueTextRanges();
    }
    return result;
  }

  @Override
  public @NotNull List<Pair<TextRange, String>> getDecodedFragments() {
    List<Pair<TextRange, String>> result = myDecodedFragments;
    if (result == null) {
      myDecodedFragments = result = PyStringLiteralExpression.super.getDecodedFragments();
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
  public @NotNull String getStringValue() {
    //ASTNode child = getNode().getFirstChildNode();
    //assert child != null;
    String result = myStringValue;
    if (result == null) {
      myStringValue = result = PyStringLiteralExpression.super.getStringValue();
    }
    return result;
  }

  @Override
  public @Nullable Object getValue() {
    return getStringValue();
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getStringValue();
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyFile file = PyUtil.as(FileContextUtil.getContextFile(this), PyFile.class);
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(file == null ? this : file);
    final LanguageLevel languageLevel = file == null ? LanguageLevel.forElement(this) : file.getLanguageLevel();

    final ASTNode firstNode = ContainerUtil.getFirstItem(getStringNodes());
    if (firstNode != null) {
      if (firstNode.getElementType() == PyElementTypes.FSTRING_NODE) {
        String prefix = PyStringLiteralCoreUtil.getPrefix(firstNode.getText());
        if (PyStringLiteralUtil.isTemplatePrefix(prefix)) {
          if (languageLevel.isOlderThan(LanguageLevel.PYTHON314)) {
            return PyBuiltinCache.getInstance(this).getStrType();
          }
          PyClassType templateClassType = getTemplateClassType();
          if (templateClassType != null) {
            return templateClassType;
          }
        }
        // f-strings can't have "b" prefix, so they are always unicode
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

  private @Nullable PyClassType getTemplateClassType() {
    PyPsiFacade facade = PyPsiFacade.getInstance(getProject());
    PyClass templateClass = facade.createClassByQName(PyNames.TEMPLATELIB_TEMPLATE, getContainingFile());
    if (templateClass != null) {
      return facade.createClassType(templateClass, false);
    }
    return null;
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
      @Override
      public @NotNull String getPresentableText() {
        return getStringValue();
      }

      @Override
      public @Nullable String getLocationString() {
        String packageForFile = PyElementPresentation.getPackageForFile(getContainingFile());
        return packageForFile != null ? String.format("(%s)", packageForFile) : null;
      }

      @Override
      public @NotNull Icon getIcon(boolean unused) {
        return AllIcons.Nodes.Variable;
      }
    };
  }

  @Override
  public int valueOffsetToTextOffset(int valueOffset) {
    return createLiteralTextEscaper().getOffsetInHost(valueOffset, getStringValueTextRange());
  }

}
