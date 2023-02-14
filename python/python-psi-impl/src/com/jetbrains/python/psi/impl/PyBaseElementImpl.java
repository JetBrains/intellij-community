// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyTypeCommentOwnerStub;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PyBaseElementImpl<T extends StubElement> extends StubBasedPsiElementBase<T> implements PyElement {
  public PyBaseElementImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PyBaseElementImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public PythonLanguage getLanguage() {
    return (PythonLanguage)PythonFileType.INSTANCE.getLanguage();
  }

  @Override
  public String toString() {
    String className = getClass().getName();
    int pos = className.lastIndexOf('.');
    if (pos >= 0) {
      className = className.substring(pos + 1);
    }
    className = StringUtil.trimEnd(className, "Impl");
    return className;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(this));
    if (visitor instanceof PyElementVisitor) {
      acceptPyVisitor(((PyElementVisitor)visitor));
    }
    else {
      super.accept(visitor);
    }
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyElement(this);
  }

  protected <T extends PyElement> T @NotNull [] childrenToPsi(TokenSet filterSet, T[] array) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    return PyPsiUtils.nodesToPsi(nodes, array);
  }

  @Nullable
  protected <T extends PyElement> T childToPsi(TokenSet filterSet, int index) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    if (nodes.length <= index) {
      return null;
    }
    //noinspection unchecked
    return (T)nodes[index].getPsi();
  }

  @Nullable
  protected <T extends PyElement> T childToPsi(IElementType elType) {
    final ASTNode node = getNode().findChildByType(elType);
    if (node == null) {
      return null;
    }

    //noinspection unchecked
    return (T)node.getPsi();
  }

  @Nullable
  protected <T extends PyElement> T childToPsi(@NotNull TokenSet elTypes) {
    final ASTNode node = getNode().findChildByType(elTypes);
    //noinspection unchecked
    return node != null ? (T)node.getPsi() : null;
  }

  @NotNull
  protected <T extends PyElement> T childToPsiNotNull(TokenSet filterSet, int index) {
    final PyElement child = childToPsi(filterSet, index);
    if (child == null) {
      throw new RuntimeException("child must not be null: expression text " + getText());
    }
    //noinspection unchecked
    return (T)child;
  }

  @NotNull
  protected <T extends PyElement> T childToPsiNotNull(IElementType elType) {
    final PyElement child = childToPsi(elType);
    if (child == null) {
      throw new RuntimeException("child must not be null; expression text " + getText());
    }
    //noinspection unchecked
    return (T)child;
  }

  /**
   * Overrides the findReferenceAt() logic in order to provide a resolve context with origin file for returned references.
   * The findReferenceAt() is usually invoked from UI operations, and it helps to be able to do deeper analysis in the
   * current file for such operations.
   *
   * @param offset the offset to find the reference at
   * @return the reference or null.
   */
  @Override
  public PsiReference findReferenceAt(int offset) {
    // copy/paste from SharedPsiElementImplUtil
    PsiElement element = findElementAt(offset);
    if (element == null || element instanceof OuterLanguageElement) return null;
    offset = getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

    List<PsiReference> referencesList = new ArrayList<>();
    final PsiFile file = element.getContainingFile();
    final var context =
      file != null ? TypeEvalContext.codeAnalysis(file.getProject(), file) : TypeEvalContext.codeInsightFallback(element.getProject());
    final PyResolveContext resolveContext = PyResolveContext.implicitContext(context);
    while (element != null) {
      addReferences(offset, element, referencesList, resolveContext);
      offset = element.getStartOffsetInParent() + offset;
      if (element instanceof PsiFile) break;
      element = element.getParent();
    }

    if (referencesList.isEmpty()) return null;
    if (referencesList.size() == 1) return referencesList.get(0);
    return new PsiMultiReference(referencesList.toArray(PsiReference.EMPTY_ARRAY),
                                 referencesList.get(referencesList.size() - 1).getElement());
  }

  private static void addReferences(int offset, PsiElement element, final Collection<PsiReference> outReferences,
                                    PyResolveContext resolveContext) {
    final PsiReference[] references;
    if (element instanceof PyReferenceOwner owner) {
      final PsiPolyVariantReference reference = owner.getReference(resolveContext);
      references = new PsiReference[]{reference};
    }
    else {
      references = element.getReferences();
    }
    for (final PsiReference reference : references) {
      for (TextRange range : ReferenceRange.getRanges(reference)) {
        assert range != null : reference;
        if (range.containsOffset(offset)) {
          outReferences.add(reference);
        }
      }
    }
  }

  @Nullable
  protected static <T extends StubBasedPsiElement<? extends PyAnnotationOwnerStub> & PyAnnotationOwner>
  String getAnnotationContentFromStubOrPsi(@NotNull T elem) {
    final PyAnnotationOwnerStub stub = elem.getStub();
    if (stub != null) {
      return stub.getAnnotation();
    }
    return getAnnotationContentFromPsi(elem);
  }

  @Nullable
  protected static <T extends PyAnnotationOwner> String getAnnotationContentFromPsi(@NotNull T elem) {
    final PyAnnotation annotation = elem.getAnnotation();
    if (annotation != null) {
      final PyExpression annotationValue = annotation.getValue();
      if (annotationValue != null) {
        return annotationValue.getText();
      }
    }
    return null;
  }

  @Nullable
  protected static <T extends StubBasedPsiElement<? extends PyTypeCommentOwnerStub> & PyTypeCommentOwner>
  String getTypeCommentAnnotationFromStubOrPsi(@NotNull T elem) {
    final PyTypeCommentOwnerStub stub = elem.getStub();
    if (stub != null) {
      return stub.getTypeComment();
    }
    final PsiComment comment = elem.getTypeComment();
    if (comment != null) {
      return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
    }
    return null;
  }
}
