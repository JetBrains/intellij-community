package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author vlan
 */
public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    return new PyInspectionVisitor(holder) {
      // TODO: Show types in tooltips for variables
      // TODO: Visit decorators with arguments
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        List<PyFunction> functions = new ArrayList<PyFunction>();
        final PyExpression callee = node.getCallee();
        if (callee instanceof PyReferenceExpression) {
          PsiElement e = ((PyReferenceExpression)callee).followAssignmentsChain(myTypeEvalContext).getElement();
          if (e instanceof PyFunction) {
            functions.add((PyFunction)e);
          }
        }
        if (!functions.isEmpty()) {
          PyFunction fun = functions.get(0);
          final PyArgumentList args = node.getArgumentList();
          if (args != null) {
            final PyArgumentList.AnalysisResult res = args.analyzeCall(myTypeEvalContext);
            final Map<PyExpression, PyNamedParameter> mapped = res.getPlainMappedParams();
            for (Map.Entry<PyExpression, PyNamedParameter> entry : mapped.entrySet()) {
              final PyNamedParameter p = entry.getValue();
              if (p.isPositionalContainer() || p.isKeywordContainer()) {
                // TODO: Support *args, **kwargs
                continue;
              }
              final PyType argType = entry.getKey().getType(myTypeEvalContext);
              final PyType paramType = p.getType(myTypeEvalContext);
              checkTypes(paramType, argType, entry.getKey(), myTypeEvalContext);
            }
          }
        }
      }

      @Override
      public void visitPyBinaryExpression(PyBinaryExpression node) {
        // TODO: Support operators besides PyBinaryExpression
        final PsiReference ref = node.getReference(PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext));
        final PyExpression arg = node.getRightExpression();
        checkSingleArgumentFunction(ref, arg);
      }

      @Override
      public void visitPySubscriptionExpression(PySubscriptionExpression node) {
        final PsiReference ref = node.getReference(PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext));
        final PyExpression arg = node.getIndexExpression();
        checkSingleArgumentFunction(ref, arg);
      }

      private void checkSingleArgumentFunction(PsiReference functionReference, PyExpression argument) {
        if (functionReference != null && argument != null) {
          final PsiElement resolved = functionReference.resolve();
          if (resolved instanceof PyFunction) {
            final PyFunction fun = (PyFunction)resolved;
            final PyParameter[] parameters = fun.getParameterList().getParameters();
            if (parameters.length == 2) {
              final PyNamedParameter p = parameters[1].getAsNamed();
              if (p != null) {
                final PyType argType = argument.getType(myTypeEvalContext);
                final PyType paramType = p.getType(myTypeEvalContext);
                checkTypes(paramType, argType, argument, myTypeEvalContext);
              }
            }
          }
        }
      }

      private void checkTypes(PyType superType, PyType subType, PsiElement node, TypeEvalContext context) {
        if (subType != null && superType != null) {
          if (!PyTypeChecker.match(superType, subType, context)) {
            registerProblem(node, String.format("Expected type '%s', got '%s' instead",
                                                PythonDocumentationProvider.getTypeName(superType, context),
                                                PythonDocumentationProvider.getTypeName(subType, myTypeEvalContext)));
          }
        }
      }
    };
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder problemsHolder) {
    if (LOG.isDebugEnabled()) {
      final Long startTime = session.getUserData(TIME_KEY);
      if (startTime != null) {
        LOG.debug(String.format("[%d] elapsed time: %d ms\n",
                                Thread.currentThread().getId(),
                                (System.nanoTime() - startTime) / 1000000));
      }
    }
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Type checker";
  }
}
