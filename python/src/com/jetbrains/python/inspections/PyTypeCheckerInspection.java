package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      // TODO: Visit operators (requires resolve() for operators)
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        final TypeEvalContext fastContext = TypeEvalContext.fast();
        final TypeEvalContext slowContext = TypeEvalContext.slow();
        List<PyFunction> functions = new ArrayList<PyFunction>();
        final PyExpression callee = node.getCallee();
        if (callee instanceof PyReferenceExpression) {
          PsiElement e = ((PyReferenceExpression)callee).followAssignmentsChain(slowContext).getElement();
          if (e instanceof PyFunction) {
            functions.add((PyFunction)e);
          }
        }
        if (!functions.isEmpty()) {
          PyFunction fun = functions.get(0);
          final TypeEvalContext context = fun.getContainingFile() == node.getContainingFile() ?
                                          slowContext : fastContext;
          final PyArgumentList args = node.getArgumentList();
          if (args != null) {
            final PyArgumentList.AnalysisResult res = args.analyzeCall(context);
            final Map<PyExpression, PyNamedParameter> mapped = res.getPlainMappedParams();
            for (Map.Entry<PyExpression, PyNamedParameter> entry : mapped.entrySet()) {
              final PyNamedParameter p = entry.getValue();
              if (p.isPositionalContainer() || p.isKeywordContainer()) {
                // TODO: Support *args, **kwargs
                continue;
              }
              final PyType argType = entry.getKey().getType(slowContext);
              final PyType paramType = p.getType(context);
              if (argType != null && paramType != null) {
                if (!match(paramType, argType, context)) {
                  registerProblem(entry.getKey(), String.format("Expected type '%s', got '%s' instead",
                                                                PythonDocumentationProvider.getTypeName(paramType, context),
                                                                PythonDocumentationProvider.getTypeName(argType, slowContext)));
                }
              }
            }
          }
        }
      }
    };
  }

  public static boolean match(PyType superType, PyType subType, TypeEvalContext context) {
    // TODO: subscriptable types?, module types?, etc.
    if (superType == null || subType == null) {
      return true;
    }
    if (superType instanceof PyTypeReference) {
      return match(((PyTypeReference)superType).resolve(null, context), subType, context);
    }
    if (subType instanceof PyTypeReference) {
      return match(superType, ((PyTypeReference)subType).resolve(null, context), context);
    }
    if (subType instanceof PyUnionType) {
      for (PyType t : ((PyUnionType)subType).getMembers()) {
        if (!match(superType, t, context)) {
          return false;
        }
      }
      return true;
    }
    if (superType instanceof PyUnionType) {
      for (PyType t : ((PyUnionType)superType).getMembers()) {
        if (match(t, subType, context)) {
          return true;
        }
      }
      return false;
    }
    if (superType instanceof PyClassType && subType instanceof PyClassType) {
      final PyClass superClass = ((PyClassType)superType).getPyClass();
      final PyClass subClass = ((PyClassType)subType).getPyClass();
      if (superType instanceof PyCollectionType && subType instanceof PyCollectionType) {
        if (!matchClasses(superClass, subClass)) {
          return false;
        }
        final PyType superElementType = ((PyCollectionType)superType).getElementType(context);
        final PyType subElementType = ((PyCollectionType)subType).getElementType(context);
        return match(superElementType, subElementType, context);
      }
      else if (superType instanceof PyTupleType && subType instanceof PyTupleType) {
        final PyTupleType superTupleType = (PyTupleType)superType;
        final PyTupleType subTupleType = (PyTupleType)subType;
        if (superTupleType.getElementCount() != subTupleType.getElementCount()) {
          return false;
        }
        else {
          for (int i = 0; i < superTupleType.getElementCount(); i++) {
            if (!match(superTupleType.getElementType(i), subTupleType.getElementType(i), context)) {
              return false;
            }
          }
          return true;
        }
      }
      else if (matchClasses(superClass, subClass)) {
        return true;
      }
    }
    if (superType.equals(subType)) {
      return true;
    }
    final String superName = superType.getName();
    final String subName = subType.getName();
    // TODO: No inheritance check for builtin numerics at this moment
    final boolean subIsBool = "bool".equals(subName);
    final boolean subIsInt = "int".equals(subName);
    final boolean subIsLong = "long".equals(subName);
    final boolean subIsFloat = "float".equals(subName);
    if (superName == null || subName == null ||
        superName.equals(subName) ||
        ("int".equals(superName) && subIsBool) ||
        ("long".equals(superName) && (subIsBool || subIsInt)) ||
        ("float".equals(superName) && (subIsBool || subIsInt || subIsLong)) ||
        ("complex".equals(superName) && (subIsBool || subIsInt || subIsLong || subIsFloat))) {
      return true;
    }
    return false;
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

  private static boolean matchClasses(@Nullable PyClass superClass, @Nullable PyClass subClass) {
    if (superClass == null || subClass == null || subClass.isSubclass(superClass) || PyABCUtil.isSubclass(subClass, superClass)) {
      return true;
    }
    else {
      final String superName = superClass.getName();
      return superName != null && superName.equals(subClass.getName());
    }
  }
}
