// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;

/**
 * @author vlan
 */
public abstract class PyReferenceBase implements PsiReferenceEx, PsiPolyVariantReference {
  // it is *not* final so that it can be changed in debug time. if set to false, caching is off
  @SuppressWarnings("FieldCanBeLocal")
  private static final boolean USE_CACHE = true;
  protected final PyResolveContext myContext;

  public PyReferenceBase(@NotNull PyResolveContext context) {
    myContext = context;
  }

  @NotNull
  @Override
  public abstract PyReferenceOwner getElement();

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    final ASTNode nameElement = getElement().getNameElement();
    final TextRange range = nameElement != null ? nameElement.getTextRange() : getElement().getNode().getTextRange();
    return range.shiftRight(-getElement().getNode().getStartOffset());
  }

  /**
   * Resolves reference to the most obvious point.
   * Imported module names: to module file (or directory for a qualifier).
   * Other identifiers: to most recent definition before this reference.
   * This implementation is cached.
   *
   * @see #resolveInner().
   */
  @Override
  @Nullable
  public PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
  }

  /**
   * Resolves reference to possible referred elements.
   * First element is always what resolve() would return.
   * Imported module names: to module file, or {directory, '__init__.py}' for a qualifier.
   * todo Local identifiers: a list of definitions in the most recent compound statement
   * (e.g. {@code if X: a = 1; else: a = 2} has two definitions of {@code a}.).
   * todo Identifiers not found locally: similar definitions in imported files and builtins.
   *
   * @see PsiPolyVariantReference#multiResolve(boolean)
   */
  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    if (USE_CACHE) {
      final ResolveCache cache = ResolveCache.getInstance(getElement().getProject());
      return cache.resolveWithCaching(this, CachingResolver.INSTANCE, true, incompleteCode);
    }
    else {
      return multiResolveInner();
    }
  }

  @NotNull
  protected abstract ResolveResult[] multiResolveInner();

  @NotNull
  protected abstract List<RatedResolveResult> resolveInner();

  @Override
  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = getElement().getNameElement();
    newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
    if (nameElement != null && PyNames.isIdentifier(newElementName)) {
      final ASTNode newNameElement = PyUtil.createNewName(getElement(), newElementName);
      getElement().getNode().replaceChild(nameElement, newNameElement);
    }
    return getElement();
  }

  @Override
  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  protected boolean resolvesToWrapper(PsiElement element, PsiElement resolveResult) {
    if (element instanceof PyFunction && ((PyFunction) element).getContainingClass() != null && resolveResult instanceof PyTargetExpression) {
      final PyExpression assignedValue = ((PyTargetExpression)resolveResult).findAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        final PyCallExpression call = (PyCallExpression)assignedValue;
        final Pair<String,PyFunction> functionPair = PyCallExpressionHelper.interpretAsModifierWrappingCall(call);
        if (functionPair != null && functionPair.second == element) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Throws away fake elements used for completion internally.
   */
  protected List<LookupElement> getOriginalElements(@NotNull CompletionVariantsProcessor processor) {
    final List<LookupElement> ret = Lists.newArrayList();
    for (LookupElement item : processor.getResultList()) {
      final PsiElement e = item.getPsiElement();
      if (e != null) {
        final PsiElement original = CompletionUtil.getOriginalElement(e);
        if (original == null) {
          continue;
        }
      }
      ret.add(item);
    }
    return ret;
  }

  @Override
  @Nullable
  public String getUnresolvedDescription() {
    return null;
  }

  protected static Object[] getTypeCompletionVariants(PyExpression pyExpression, PyType type) {
    ProcessingContext context = new ProcessingContext();
    context.put(PyType.CTX_NAMES, new HashSet<>());
    return type.getCompletionVariants(pyExpression.getName(), pyExpression, context);
  }

  private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceBase> {
    public static final CachingResolver INSTANCE = new CachingResolver();

    @Override
    @NotNull
    public ResolveResult[] resolve(@NotNull final PyReferenceBase ref, final boolean incompleteCode) {
      return ref.multiResolveInner();
    }
  }
}
