package com.jetbrains.python.psi.impl;

import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
  public List<? extends PsiElement> resolveMember(final String name, AccessDirection direction) {
    final PsiMethod[] methods = myClass.findMethodsByName(name, true);
    if (methods.length > 0) {
      return Arrays.asList(methods); // TODO[yole]: correct resolve
    }
    final PsiField field = myClass.findFieldByName(name, true);
    if (field != null) return new SmartList<PsiElement>(field);
    return null;
  }

  public Object[] getCompletionVariants(final PyQualifiedExpression referenceExpression, ProcessingContext context) {
    final VariantsProcessor processor = new VariantsProcessor(referenceExpression);
    myClass.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    return processor.getResult();
  }

  public String getName() {
    if (myClass != null)
    return myClass.getName();
    else return null;
  }
}
