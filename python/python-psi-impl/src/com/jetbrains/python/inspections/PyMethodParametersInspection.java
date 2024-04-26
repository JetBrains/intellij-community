// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.AddSelfQuickFix;
import com.jetbrains.python.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Looks for the 'self' or its equivalents.
 */
public final class PyMethodParametersInspection extends PyInspection {

  @Nullable
  public static PyMethodParametersInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    final String toolName = PyMethodParametersInspection.class.getSimpleName();
    return (PyMethodParametersInspection)inspectionProfile.getUnwrappedTool(toolName, element);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {

    private Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFunction(final @NotNull PyFunction node) {
      for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
        if (extension.ignoreMethodParameters(node, myTypeEvalContext)) {
          return;
        }
      }

      // analyze function itself
      PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(node);
      if (flags != null) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        final String methodName = node.getName();
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
              if (flags.isMetaclassMethod() || flags.isClassMethod()) {
                paramName = PyNames.CANONICAL_CLS;
              }
              else {
                paramName = PyNames.CANONICAL_SELF;
              }
              registerProblem(
                plist, PyPsiBundle.message("INSP.must.have.first.parameter", paramName),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix(paramName)
              );
            }
          }
        }
        else { // fix: rename
          PyNamedParameter first_param = params[0].getAsNamed();
          if (first_param != null) {
            String pname = first_param.getName();
            if (pname == null) {
              return;
            }
            // every dup, swap, drop, or dup+drop of "self"
            @NonNls String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
            if (PyUtil.among(pname, mangled)) {
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyPsiBundle.message("INSP.probably.mistyped.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
              return;
            }
            if (flags.isMetaclassMethod()) {
              if (flags.isStaticMethod() && !PyNames.NEW.equals(methodName)) {
                return;
              }
              String expectedName;
              Set<String> alternativeNames;
              if (PyNames.NEW.equals(methodName) || flags.isClassMethod()) {
                expectedName = PyNames.CANONICAL_CLS;
                alternativeNames = Set.of("mcs", "mcls", "metacls");
              }
              else if (flags.isSpecialMetaclassMethod()) {
                expectedName = PyNames.CANONICAL_CLS;
                alternativeNames = Set.of();
              }
              else {
                expectedName = PyNames.CANONICAL_SELF;
                alternativeNames = Set.of(PyNames.CANONICAL_CLS);
              }
              if (!expectedName.equals(pname) && !alternativeNames.contains(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyPsiBundle.message("INSP.usually.named", expectedName),
                  new RenameParameterQuickFix(expectedName)
                );
              }
            }
            else if (flags.isClassMethod() || PyNames.NEW.equals(methodName)) {
              if (!PyNames.CANONICAL_CLS.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyPsiBundle.message("INSP.usually.named", PyNames.CANONICAL_CLS),
                  new RenameParameterQuickFix(PyNames.CANONICAL_CLS)
                );
              }
            }
            else if (!flags.isStaticMethod() && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyPsiBundle.message("INSP.usually.named.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
            }
          }
          else { // the unusual case of a method with first tuple param
            if (!flags.isStaticMethod()) {
              registerProblem(plist, PyPsiBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }
}
