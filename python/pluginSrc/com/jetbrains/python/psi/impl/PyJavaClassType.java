package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyJavaClassType implements PyCallableType {
  private final PsiClass myClass;
  private final boolean myDefinition;

  public PyJavaClassType(final PsiClass aClass, boolean definition) {
    myClass = aClass;
    myDefinition = definition;
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    final PsiMethod[] methods = myClass.findMethodsByName(name, true);
    if (methods.length > 0) {
      ResolveResultList resultList = new ResolveResultList();
      for (PsiMethod method : methods) {
        resultList.poke(method, RatedResolveResult.RATE_NORMAL);
      }
      return resultList;
    }
    final PsiField field = myClass.findFieldByName(name, true);
    if (field != null) return ResolveResultList.to(field);
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location);
    myClass.processDeclarations(processor, ResolveState.initial(), null, location);
    return processor.getResult();
  }

  public String getName() {
    if (myClass != null) {
      return myClass.getName();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;  // TODO: JDK's types could be considered built-in.
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public boolean isCallable() {
    return myDefinition;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    if (myDefinition) {
      return new PyJavaClassType(myClass, false);
    }
    return null;
  }
}
