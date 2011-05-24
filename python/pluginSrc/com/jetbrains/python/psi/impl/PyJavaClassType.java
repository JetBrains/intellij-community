package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyJavaClassType implements PyType {
  private final PsiClass myClass;

  public PyJavaClassType(final PsiClass aClass) {
    myClass = aClass;
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(final String name,
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
    final VariantsProcessor processor = new VariantsProcessor(location);
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
    return false;  // TODO: JSDK's types could be considered built-in.
  }
}
