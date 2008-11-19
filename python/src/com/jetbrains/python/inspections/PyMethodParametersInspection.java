package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Looks for the 'self'.
 * User: dcheryasov
 * Date: Nov 17, 2008
 */
public class PyMethodParametersInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return "Python"; // TODO: propertize
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Method lacking first parameter"; // TODO: propertize
  }

  @NotNull
  public String getShortName() {
    return "PyMethodParametersInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.INFO;
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

    @Override
    public void visitPyFunction(final PyFunction node) {
      PsiElement cap = PyResolveUtil.getConcealingParent(node);
      if (cap instanceof PyClass) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        if (params.length == 0) {
          // TODO: check for "staticmetod"
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            registerProblem(name_node.getPsi(), "Method must have a first parameter, usually called 'self'", ProblemHighlightType.ERROR, null);
          }
        }
        else {
          String pname = params[0].getText();
          // TODO: generate a better list, or use Levenstein's distance, etc.
          for (String typo : new String[] {"elf", "sef", "sel", "slf", "sself", "seelf", "sellf"}) {
            if (typo.equals(pname)) {
              registerProblem(params[0].getNode().getPsi(), "Did not you mean 'self'?");
              // NOTE: test framework rejects weak warnings, which would be more appropriate
              return;
            }
          }
          // TODO: check for "classmethod" or "staticmetod" and style settings
          if (!"self".equals(pname)) {
            registerProblem(plist, "Usually first parameter of a method is named 'self'");
          }
        }
      }
    }
  }
}
