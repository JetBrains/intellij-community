package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      private String getName() {
        PyElement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
        if (elt instanceof PyFromImportStatement) { // always? who knows :)
          PyReferenceExpression imp_src = ((PyFromImportStatement)elt).getImportSource();
          if (imp_src != null) {
            return PyResolveUtil.toPath(imp_src, ".");
          }
        }
        return "<?>";
      }

      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        StringBuffer buf = new StringBuffer("| ");
        buf.append("from ").append(getName()).append(" import *");
        return buf.toString();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }
}
