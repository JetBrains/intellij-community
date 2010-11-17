package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.Callable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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
    return myCallable.getReturnType(typeEvalContext);
  }

  public String getName() {
    return "return type of " + myCallable.getPresentation().getPresentableText();
  }

  @Override
  public boolean isBuiltin() {
    PyType type = myCallable.getReturnType(TypeEvalContext.slow());
    return type != null && type.isBuiltin();
  }
}
