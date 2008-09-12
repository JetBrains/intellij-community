package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simplest PyStarImportElement possible. 
 * User: dcheryasov
 * Date: Jul 28, 2008
 */
public class PyStarImportElementImpl extends PyElementImpl implements PyStarImportElement {

  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new ArrayIterable<PyElement>(PyElement.EMPTY_ARRAY);
  }

  @Nullable
  public PsiElement getElementNamed(final String the_name) {
    PyFromImportStatement import_from_stmt = PsiTreeUtil.getParentOfType(this, PyFromImportStatement.class);
    if (import_from_stmt != null) {
      PyReferenceExpression from_src = import_from_stmt.getImportSource();
      // XXX won't work in Jython. Use resolvePythonImport with a mock reference
      return ResolveImportUtil.resolvePythonImport2(from_src, the_name);
    }
    else return null;
  }

  public boolean mustResolveOutside() {
    return true; // we don't have children, but... 
  }
}
