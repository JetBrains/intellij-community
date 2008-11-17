package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Annotates declarations that unconditionally ovverride other without these being used.
 * E.g.: <code>x = 1; x = 2</code>
 * User: dcheryasov
 * Date: Nov 14, 2008
 */
public class PyRedeclarationInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return "Python"; // TODO: propertize
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Detect redefinitions"; // TODO: propertize
  }

  @NotNull
  public String getShortName() {
    return "PyRedeclarationInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return false; // too immature yet
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }
    
    // TODO: This function is a shame; replace with a proper interface.
    private static String _getKind(PsiElement elt) {
      if (elt instanceof PyFunction) return "function";
      if (elt instanceof PyClass) return "class";
      if (elt instanceof PyTargetExpression) return "variable";
      return "item";
    }

    private void _checkAbove(PyElement  node, String kind) {
      String name = node.getName();
      if (name != null) {
        PyResolveUtil.ResolveProcessor proc = new PyResolveUtil.ResolveProcessor(node.getName());
        PyResolveUtil.treeCrawlUp(proc, node);
        PsiElement found = proc.getResult();
        // TODO: check if the redefined name is used somehow
        if (found != null) {
          registerProblem(node.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(), "Shadows same-named " + _getKind(found) + " above");
          //registerProblem(prev.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(), "Overridden by same-named " + kind + " below");
        }
      }
    }

    // TODO: add a rename quickfix

    @Override
    public void visitPyFunction(final PyFunction node) {
      _checkAbove(node, "function");
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      _checkAbove(node, "variable");
    }

    @Override
    public void visitPyClass(final PyClass node) {
      _checkAbove(node, "class");
    }
  }
}
