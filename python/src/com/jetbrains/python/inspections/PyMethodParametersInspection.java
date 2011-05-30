package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddSelfQuickFix;
import com.jetbrains.python.actions.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return HighlightDisplayLevel.WEAK_WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    private Ref<PsiElement> myPossibleZopeRef;

    public Visitor(final ProblemsHolder holder) {
      super(holder);
      myPossibleZopeRef = new Ref<PsiElement>();
    }

    @Nullable
    private PsiElement findZopeInterface(PsiElement foothold) {
      // NOTE: we don't sync access: we can only set it to the same value; if multiple threads do so, not much of a problem
      PsiElement ret = myPossibleZopeRef.get();
      if (ret != null) return ret;
      else {
        ret = ResolveImportUtil.resolveInRoots(foothold, "zope.interface.Interface");
        myPossibleZopeRef.set(ret); // null is OK
      }
      return ret;
    }

    private static boolean ultimatelyListsInBases(PyClass cls, PsiElement target_base) {
      for (PyClassRef cref : cls.iterateAncestors()) {
        if (cref.getElement() == target_base) return true;
        PyClass base_class = cref.getPyClass();
        if (base_class != null && ultimatelyListsInBases(base_class, target_base)) return true;
      }
      return false;
    }


    @Override
    public void visitPyFunction(final PyFunction node) {
      // maybe it's a zope interface?
      PsiElement zope_interface = findZopeInterface(node);
      if (zope_interface != null) {
        PyClass cls = node.getContainingClass();
        if (cls != null && ultimatelyListsInBases(cls, zope_interface)) return; // it can have any params
      }
      // analyze function itself
      PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(node);
      if (flags != null) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        final String method_name = node.getName();
        final String CLS = "cls"; // TODO: move to style settings
        final String MCS = "mcs"; // as per pylint inspection C0203
        if (params.length == 0) { // fix: add
          // check for "staticmetod"
          if (flags.isStaticMethod()) return; // no params may be fine
          // check actual param list
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            PsiElement open_paren = plist.getFirstChild();
            PsiElement close_paren = plist.getLastChild();
            if (
              open_paren != null && close_paren != null &&
              "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
            ) {
              String paramName;
              if (flags.isMetaclassMethod()) {
                if (flags.isClassMethod()) {
                  paramName = MCS;
                }
                else {
                  paramName = CLS;
                }
              }
              else if (flags.isClassMethod()) {
                paramName = CLS;
              }
              else {
                paramName = PyNames.CANONICAL_SELF;
              }
              registerProblem(
                plist, PyBundle.message("INSP.must.have.first.parameter", paramName),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix(paramName)
              );
            }
          }
        }
        else { // fix: rename
          PyNamedParameter first_param = params[0].getAsNamed();
          if (first_param != null) {
            String pname = first_param.getText();
            // every dup, swap, drop, or dup+drop of "self"
            @NonNls String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
            if (PyUtil.among(pname, mangled)) {
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyBundle.message("INSP.probably.mistyped.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
              return;
            }
            if (flags.isMetaclassMethod()) {
              String expected_name;
              if (PyNames.NEW.equals(method_name) || flags.isClassMethod()) {
                expected_name = MCS;
              }
              else if (flags.isSpecialMetaclassMethod()) {
                expected_name = CLS;
              }
              else {
                expected_name = PyNames.CANONICAL_SELF;
              }
              if (!expected_name.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", expected_name),
                  new RenameParameterQuickFix(expected_name)
                );
              }
            }
            else if (flags.isClassMethod() || PyNames.NEW.equals(method_name)) {
              if (!CLS.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.$0", CLS),
                  new RenameParameterQuickFix(CLS)
                );
              }
            }
            else if (!flags.isStaticMethod() && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
              if (flags.isMetaclassMethod() && CLS.equals(pname)) {
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
            if (!flags.isStaticMethod()) {
              registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }

}
