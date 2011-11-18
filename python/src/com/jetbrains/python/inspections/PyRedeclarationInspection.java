package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotates declarations that unconditionally ovverride other without these being used.
 * E.g.: <code>x = 1; x = 2</code>
 * User: dcheryasov
 * Date: Nov 14, 2008
 */
public class PyRedeclarationInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.redeclaration");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false; // too immature yet
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: This function is a shame; replace with a proper interface.
    private static String _getKind(PsiElement elt) {
      if (elt instanceof PyFunction) return PyBundle.message("GNAME.function");
      if (elt instanceof PyClass) return PyBundle.message("GNAME.class");
      if (elt instanceof PyTargetExpression) return PyBundle.message("GNAME.var");
      return PyBundle.message("GNAME.item");
    }

    private void _checkAbove(PyElement node, String kind) {
      String name = node.getName();
      if (name != null) {
        ResolveProcessor proc = new ResolveProcessor(node.getName());
        PyResolveUtil.treeCrawlUp(proc, node);
        PsiElement found = proc.getResult();
        // TODO: check if the redefined name is used somehow
        if (found != null && ! (found instanceof PyTargetExpression)) {
          registerProblem(
            node.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(),
            PyBundle.message("INSP.shadows.same.named.$0.above", _getKind(found))
          );
          //registerProblem(prev.getNode().findChildByType(PyTokenTypes.IDENTIFIER).getPsi(), "Overridden by same-named " + kind + " below");
        }
      }
    }

    // TODO: add a rename quickfix

    @Override
    public void visitPyFunction(final PyFunction node) {
      _checkAbove(node, PyBundle.message("GNAME.function"));
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      _checkAbove(node, PyBundle.message("GNAME.var"));
    }

    @Override
    public void visitPyClass(final PyClass node) {
      _checkAbove(node, PyBundle.message("GNAME.class"));
    }
  }
}
