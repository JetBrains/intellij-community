package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author dcheryasov
 */
public class PyStarImportElementImpl extends PyElementImpl implements PyStarImportElement {

  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement import_from_stmt = (PyFromImportStatement)getParent();
      final PsiElement source = ResolveImportUtil.resolveFromImportStatementSource(import_from_stmt);
      if (source instanceof PyFile) {
        return ((PyFile)source).iterateNames();
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement getElementNamed(final String the_name) {
    if (PyUtil.isClassPrivateName(the_name)) {
      return null;
    }
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement import_from_stmt = (PyFromImportStatement)getParent();
      PyReferenceExpression from_src = import_from_stmt.getImportSource();
      final List<PsiElement> importedFiles = ResolveImportUtil.resolveImportReference(from_src);
      for (PsiElement importedFile : new HashSet<PsiElement>(importedFiles)) { // resolver gives lots of duplicates
        final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
        if (source instanceof PyFile) {
          PyFile sourceFile = (PyFile)source;
          final PsiElement exportedName = sourceFile.findExportedName(the_name);
          if (exportedName != null) {
            final List<String> all = sourceFile.getDunderAll();
            if (all != null && !all.contains(the_name)) {
              continue;
            }
            return exportedName;
          }
        }
      }
    }
    return null;
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

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStarImportElement(this);
  }
}
