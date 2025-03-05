// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;


@ApiStatus.Experimental
public interface PyAstImportElement extends PyAstElement, PyAstImportedNameDefiner {
  default @Nullable PyAstReferenceExpression getImportReferenceExpression() {
    final ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
    return node == null ? null : (PyAstReferenceExpression) node.getPsi();
  }

  default @Nullable QualifiedName getImportedQName() {
    final PyAstReferenceExpression importReference = getImportReferenceExpression();
    return importReference != null ? importReference.asQualifiedName() : null;
  }

  default @Nullable PyAstTargetExpression getAsNameElement() {
    return findChildByType(this, PyElementTypes.TARGET_EXPRESSION);
  }

  default @Nullable String getAsName() {
    final PyAstTargetExpression element = getAsNameElement();
    return element != null ? element.getName() : null;
  }

  /**
   * @return name under which the element is visible, that is, "as name" is there is one, or just name.
   */
  default @Nullable String getVisibleName() {
    PyAstTargetExpression asNameElement = getAsNameElement();
    if (asNameElement != null) {
      return asNameElement.getName();
    }
    final QualifiedName importedName = getImportedQName();
    if (importedName != null && importedName.getComponentCount() > 0) {
      return importedName.getComponents().get(0);
    }
    return null;
  }

  default PyAstStatement getContainingImportStatement() {
    PsiElement parent = getParent();
    return parent instanceof PyAstStatement ? (PyAstStatement)parent : null;
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyImportElement(this);
  }
}
