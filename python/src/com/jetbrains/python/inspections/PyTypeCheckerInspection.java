package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (LOG.isDebugEnabled())
      session.putUserData(TIME_KEY, System.nanoTime());
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final PyQualifiedExpression qualified = (PyQualifiedExpression)callee;
        if (isResolvedToSeveralMethods(qualified)) {
          return;
        }
        checkCallSite(qualified);
      }
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      // TODO: Support slice PySliceExpressions
      checkCallSite(node);
    }

    /**
     * Hack for skipping type checking for method calls of union members if there are several call alternatives.
     *
     * TODO: Multi-resolve callees when analysing calls. This requires multi-resolving in followAssignmentsChain.
     */
    private boolean isResolvedToSeveralMethods(@NotNull PyQualifiedExpression callee) {
      final PyExpression qualifier = callee.getQualifier();
      if (qualifier != null) {
        final PyType qualifierType = qualifier.getType(myTypeEvalContext);
        if (qualifierType instanceof PyUnionType) {
          final PyUnionType unionType = (PyUnionType)qualifierType;
          final String name = callee.getName();
          int sameNameCount = 0;
          for (PyType member : unionType.getMembers()) {
            if (member != null) {
              final List<? extends RatedResolveResult> results = member.resolveMember(name, callee, AccessDirection.READ,
                                                                                      resolveWithoutImplicits());
              if (results != null && !results.isEmpty()) {
                sameNameCount++;
              }
            }
          }
          if (sameNameCount > 1) {
            return true;
          }
        }
        final PyExpression qualifierExpr = qualifier instanceof PyCallExpression ? ((PyCallExpression)qualifier).getCallee() : qualifier;
        if (qualifierExpr instanceof PyQualifiedExpression) {
          return isResolvedToSeveralMethods((PyQualifiedExpression)qualifierExpr);
        }
      }
      return false;
    }

    private void checkCallSite(@Nullable PyQualifiedExpression callSite) {
      final Map<PyGenericType, PyType> substitutions = new LinkedHashMap<PyGenericType, PyType>();
      final PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, myTypeEvalContext);
      if (results != null) {
        substitutions.putAll(PyTypeChecker.collectCallGenerics(results.getCallable(), results.getReceiver(), myTypeEvalContext));
        for (Map.Entry<PyExpression, PyNamedParameter> entry : results.getArguments().entrySet()) {
          final PyNamedParameter p = entry.getValue();
          if (p.isPositionalContainer() || p.isKeywordContainer()) {
            // TODO: Support *args, **kwargs
            continue;
          }
          final PyType argType = entry.getKey().getType(myTypeEvalContext);
          final PyType paramType = p.getType(myTypeEvalContext);
          checkTypes(paramType, argType, entry.getKey(), myTypeEvalContext, substitutions);
        }
      }
    }

    @Nullable
    private String checkTypes(@Nullable PyType superType, @Nullable PyType subType, @Nullable PsiElement node,
                              @NotNull TypeEvalContext context, @NotNull Map<PyGenericType, PyType> substitutions) {
      if (subType != null && superType != null) {
        if (!PyTypeChecker.match(superType, subType, context, substitutions)) {
          final String superName = PythonDocumentationProvider.getTypeName(superType, context);
          String expected = String.format("'%s'", superName);
          final boolean hasGenerics = PyTypeChecker.hasGenerics(superType, context);
          if (hasGenerics) {
            final PyType subst = PyTypeChecker.substitute(superType, substitutions, context);
            if (subst != null) {
              expected = String.format("'%s' (matched generic type '%s')",
                                       PythonDocumentationProvider.getTypeName(subst, context),
                                       superName);

            }
          }
          final String msg = String.format("Expected type %s, got '%s' instead",
                                           expected,
                                           PythonDocumentationProvider.getTypeName(subType, context));
          final ProblemHighlightType highlightType = hasGenerics ? ProblemHighlightType.WEAK_WARNING :
                                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          registerProblem(node, msg, highlightType, null);
          return msg;
        }
      }
      return null;
    }
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
