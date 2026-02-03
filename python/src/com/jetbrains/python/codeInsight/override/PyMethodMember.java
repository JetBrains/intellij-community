// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.List;

public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  private static @NlsSafe String buildNameFor(final PyElement element) {
    if (element instanceof PyFunction) {
      final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
      final List<PyCallableParameter> parameters = ((PyFunction)element).getParameters(context);

      final StringBuilder result = new StringBuilder();

      result.append(element.getName()).append('(');
      StringUtil.join(parameters, parameter -> parameter.getPresentableText(true, context), ", ", result);
      result.append(')');

      return result.toString();
    }
    return element.getName();
  }

  public PyMethodMember(final PyElement element) {
    super(element, buildNameFor(element), element.getIcon(0));
  }

  @Override
  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent);
  }
}
