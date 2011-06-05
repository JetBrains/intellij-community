package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 * @author dcheryasov
 */
public class PyInitNewSignatureInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.new.init.signature");
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
    public void visitPyClass(PyClass cls) {
      if (! cls.isNewStyleClass()) return; // old-style classes don't know about __new__
      PyFunction init_or_new = cls.findInitOrNew(false); // only local
      final PyBuiltinCache builtins = PyBuiltinCache.getInstance(cls);
      if (init_or_new == null || builtins.hasInBuiltins(init_or_new.getContainingClass())) return; // nothing is overridden
      String the_other_name = PyNames.NEW.equals(init_or_new.getName()) ? PyNames.INIT : PyNames.NEW;
      PyFunction the_other = cls.findMethodByName(the_other_name, true);
      if (the_other == null || builtins.getClass("object") == the_other.getContainingClass()) return;
      final PyParameterList closer_list = init_or_new.getParameterList();
      final PyParameterList farther_list = the_other.getParameterList();
      if (! farther_list.isCompatibleTo(closer_list) &&
          ! closer_list.isCompatibleTo(farther_list) &&
          closer_list.getContainingFile() == cls.getContainingFile()
      ) {
        registerProblem(closer_list, PyNames.NEW.equals(init_or_new.getName()) ?
                                     PyBundle.message("INSP.new.incompatible.to.init") :
                                     PyBundle.message("INSP.init.incompatible.to.new")
        );
      }
    }
  }

}