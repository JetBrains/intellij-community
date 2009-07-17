package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddSelfQuickFix;
import com.jetbrains.python.actions.RenameToSelfQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.problematic.first.parameter");
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

    protected static boolean decoratedJustWith(@NotNull final PyFunction node, @NotNull @NonNls String name) {
      PyDecoratorList decolist = node.getDecoratorList();
      if (decolist != null) {
        PyDecorator[] decos = decolist.getDecorators();
        // TODO: look for all decorators
        if (decos.length == 1) {
          PyDecorator deco = decos[0];
          String deconame = deco.getName();
          if (deco.isBuiltin() && name.equals(deconame)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      PsiElement cap = PyResolveUtil.getConcealingParent(node);
      if (cap instanceof PyClass) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        if (params.length == 0) {
          // check for "staticmetod"
          if (decoratedJustWith(node, PyNames.STATICMETHOD)) return; // no params may be fine
          // check actual param list
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            PsiElement open_paren = plist.getFirstChild();
            PsiElement close_paren = plist.getLastChild();
            if (
              open_paren != null && close_paren != null &&
              "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
            ) {
              registerProblem(
                plist, PyBundle.message("INSP.must.have.first.parameter"),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix()
              );
            }
          }
        }
        else {
          PyNamedParameter first_param = params[0].getAsNamed();
          if (first_param != null) {
            String pname = first_param.getText();
            // every dup, swap, drop, or dup+drop of "self"
            @NonNls String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
            for (String typo : mangled) {
              if (typo.equals(pname)) {
                registerProblem(params[0].getNode().getPsi(), PyBundle.message("INSP.probably.mistyped.self"), new RenameToSelfQuickFix());
                return;
              }
            }
            // TODO: check for style settings
            if (decoratedJustWith(node, PyNames.CLASSMETHOD)) {
              if (!"cls".equals(pname)) {
                registerProblem(plist, PyBundle.message("INSP.usually.named.cls"));
              }
            }
            else if (!"self".equals(pname) && !decoratedJustWith(node, PyNames.STATICMETHOD)) {
              registerProblem(plist, PyBundle.message("INSP.usually.named.self"));
            }
          }
          else { // the unusual case of a method with first tuple param
            if (!decoratedJustWith(node, PyNames.STATICMETHOD)) {
              registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }

}
