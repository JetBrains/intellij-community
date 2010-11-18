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

  private interface ReturnTypeCallback {
    @Nullable
    PyType getType(PyReferenceExpression callSite, TypeEvalContext context);
  }

  private static class ReturnTypeDescriptor {
    private final Map<String, ReturnTypeCallback> myStringToReturnTypeMap = new HashMap<String, ReturnTypeCallback>();

    void put(String className, ReturnTypeCallback callback) {
      myStringToReturnTypeMap.put(className, callback);
    }

    @Nullable
    public PyType get(PyFunction function, PyReferenceExpression callSite, TypeEvalContext context) {
      PyClass containingClass = function.getContainingClass();
      if (containingClass != null) {
        final ReturnTypeCallback typeCallback = myStringToReturnTypeMap.get(containingClass.getQualifiedName());
        if (typeCallback != null) {
          return typeCallback.getType(callSite, context);
        }
      }
      return null;
    }
  }

  private final ReturnTypeCallback mySelfTypeCallback = new ReturnTypeCallback() {
    @Override
    public PyType getType(PyReferenceExpression callSite, TypeEvalContext context) {
      final PyExpression qualifier = callSite.getQualifier();
      if (qualifier != null) {
        final PyType type = qualifier.getType(context);
        if (type instanceof PyClassType) {
          return new PyClassType(((PyClassType)type).getPyClass(), false);
        }
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
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context) {
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

  protected void registerSelfReturnType(String classQualifiedName, Collection<String> methods) {
    for (String method : methods) {
      myMethodToReturnTypeMap.get(method).put(classQualifiedName, mySelfTypeCallback);
    }
  }
}
