package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyTypeProviderBase implements PyTypeProvider {
  public PyTypeProviderBase() {
  }

  protected interface ReturnTypeCallback {
    @Nullable
    PyType getType(@Nullable PyReferenceExpression callSite, @Nullable PyType qualifierType, TypeEvalContext context);
  }

  private static class ReturnTypeDescriptor {
    private final Map<String, ReturnTypeCallback> myStringToReturnTypeMap = new HashMap<String, ReturnTypeCallback>();

    void put(String className, ReturnTypeCallback callback) {
      myStringToReturnTypeMap.put(className, callback);
    }

    @Nullable
    public PyType get(PyFunction function, @Nullable PyReferenceExpression callSite, TypeEvalContext context) {
      PyClass containingClass = function.getContainingClass();
      if (containingClass != null) {
        final ReturnTypeCallback typeCallback = myStringToReturnTypeMap.get(containingClass.getQualifiedName());
        if (typeCallback != null) {
          final PyExpression qualifier = callSite != null ? callSite.getQualifier() : null;
          PyType qualifierType = qualifier != null ? qualifier.getType(context) : null;
          return typeCallback.getType(callSite, qualifierType, context);
        }
      }
      return null;
    }
  }

  private final ReturnTypeCallback mySelfTypeCallback = new ReturnTypeCallback() {
    @Override
    public PyType getType(@Nullable PyReferenceExpression callSite, @Nullable PyType qualifierType, TypeEvalContext context) {
      if (qualifierType instanceof PyClassType) {
        return new PyClassType(((PyClassType)qualifierType).getPyClass(), false);
      }
      return null;
    }
  };

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<String, ReturnTypeDescriptor> myMethodToReturnTypeMap = new FactoryMap<String, ReturnTypeDescriptor>() {
    @Override
    protected ReturnTypeDescriptor create(String key) {
      return new ReturnTypeDescriptor();
    }
  };

  @Override
  public PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    return null;
  }

  @Override
  public PyType getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
    return null;
  }

  @Override
  public PyType getReturnType(PyFunction function, @Nullable PyReferenceExpression callSite, TypeEvalContext context) {
    ReturnTypeDescriptor descriptor = myMethodToReturnTypeMap.get(function.getName());
    if (descriptor != null) {
      return descriptor.get(function, callSite, context);
    }
    return null;
  }

  @Override
  public PyType getIterationType(PyClass iterable) {
    return null;
  }

  protected void registerSelfReturnType(String classQualifiedName, Collection<String> methods) {
    registerReturnType(classQualifiedName, methods, mySelfTypeCallback);
  }

  protected void registerReturnType(String classQualifiedName,
                                    Collection<String> methods,
                                    final ReturnTypeCallback callback) {
    for (String method : methods) {
      myMethodToReturnTypeMap.get(method).put(classQualifiedName, callback);
    }
  }
}
