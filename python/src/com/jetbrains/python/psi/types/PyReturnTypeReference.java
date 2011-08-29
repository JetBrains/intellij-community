package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.Callable;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyReturnTypeReference extends PyTypeReferenceImpl {
  private final Callable myCallable;

  public PyReturnTypeReference(Callable callable) {
    myCallable = callable;
  }

  @Nullable
  public PyType resolve(PsiElement context, TypeEvalContext typeEvalContext) {
    return myCallable.getReturnType(typeEvalContext, null);
  }

  public String getName() {
    return "return type of " + myCallable.getPresentation().getPresentableText();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    PyType type = myCallable.getReturnType(context, null);
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
