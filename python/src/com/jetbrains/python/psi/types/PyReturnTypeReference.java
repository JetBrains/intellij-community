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
}
