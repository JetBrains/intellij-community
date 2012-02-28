package com.jetbrains.python.psi.types;

import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.Callable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PyReturnTypeReference extends PyTypeReferenceImpl {
  private final Callable myCallable;

  public PyReturnTypeReference(Callable callable) {
    myCallable = callable;
  }

  @Nullable
  @Override
  protected PyType resolveStep(@Nullable PsiElement context, @NotNull TypeEvalContext typeEvalContext) {
    final PyType returnType = myCallable.getReturnType(typeEvalContext, null);
    return PyTypeChecker.hasGenerics(returnType, typeEvalContext) ? null : returnType;
  }

  public String getName() {
    final ItemPresentation presentation = myCallable.getPresentation();
    if (presentation != null) {
      return "return type of " + presentation.getPresentableText();
    }
    return "return type of " + myCallable.toString();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return isBuiltin(context, new HashSet<PyType>());
  }

  private boolean isBuiltin(TypeEvalContext context, Set<PyType> evaluated) {
    final PyType type = resolve(null, context);
    if (evaluated.contains(type)) {
      return false;
    }
    evaluated.add(type);
    return type != null && type.isBuiltin(context);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyReturnTypeReference that = (PyReturnTypeReference)o;

    if (myCallable != null ? !myCallable.equals(that.myCallable) : that.myCallable != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myCallable != null ? myCallable.hashCode() : 0;
  }
}
