package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddSelfQuickFix;
import com.jetbrains.python.actions.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.jetbrains.python.psi.PyFunction.Flag.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Flag.STATICMETHOD;

/**
 * Looks for the 'self' or its equivalents.
 * @author dcheryasov
 */
public class PyMethodParametersInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.problematic.first.parameter");
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


    private static boolean among(@NotNull String what, String... variants) {
      for (String s : variants) {
        if (what.equals(s)) return true;
      }
      return false;
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      PsiElement cap = PyUtil.getConcealingParent(node);
      if (cap instanceof PyClass) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        Set<PyFunction.Flag> flags = PyUtil.detectDecorationsAndWrappersOf(node);
        boolean isMetaclassMethod = false;
        PyClass type_cls = PyBuiltinCache.getInstance(node).getClass("type");
        for (PyClass ancestor_cls : ((PyClass)cap).iterateAncestors()) {
          if (ancestor_cls == type_cls) {
            isMetaclassMethod = true;
            break;
          }
        }
        final String method_name = node.getName();
        boolean isSpecialMetaclassMethod = isMetaclassMethod && among(method_name, PyNames.INIT, "__call__");
        final boolean is_staticmethod = flags.contains(STATICMETHOD);
        if (params.length == 0) {
          // check for "staticmetod"
          if (is_staticmethod) return; // no params may be fine
          // check actual param list
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            PsiElement open_paren = plist.getFirstChild();
            PsiElement close_paren = plist.getLastChild();
            if (
              open_paren != null && close_paren != null &&
              "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
            ) {
              String paramName = flags.contains(CLASSMETHOD) || isMetaclassMethod ? "cls" : "self";
              registerProblem(
                plist, PyBundle.message("INSP.must.have.first.parameter", paramName),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix(paramName)
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
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.probably.mistyped.self"),
                  new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
                );
                return;
              }
            }
            String CLS = "cls"; // TODO: move to style settings
            if (isMetaclassMethod && PyNames.NEW.equals(method_name)) {
              final String[] POSSIBLE_PARAM_NAMES = {"typ", "meta"}; // TODO: move to style settings
              if (!among(pname, POSSIBLE_PARAM_NAMES)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", POSSIBLE_PARAM_NAMES[0]),
                  new RenameParameterQuickFix(POSSIBLE_PARAM_NAMES[0])
                );
              }
            }
            else if (flags.contains(CLASSMETHOD) || isSpecialMetaclassMethod || PyNames.NEW.equals(method_name)) {
              if (!CLS.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", CLS),
                  new RenameParameterQuickFix(CLS)
                );
              }
            }
            else if (!is_staticmethod && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
              if (isMetaclassMethod && CLS.equals(pname)) {
                return;   // accept either 'self' or 'cls' for all methods in metaclass
              }
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyBundle.message("INSP.usually.named.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
            }
          }
          else { // the unusual case of a method with first tuple param
            if (!is_staticmethod) {
              registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }

}
