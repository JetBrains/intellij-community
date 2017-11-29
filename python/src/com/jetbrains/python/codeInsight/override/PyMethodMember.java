/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.override;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;

import javax.swing.*;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Alexey.Ivanov
 */
public class PyMethodMember extends PsiElementMemberChooserObject implements ClassMember {
  private final String myFullName;
  private static String buildNameFor(final PyElement element) {
    if (element instanceof PyFunction) {
      final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
      final List<PyCallableParameter> parameters = ((PyFunction)element).getParameters(context);

      final StringBuilder result = new StringBuilder();

      result.append(element.getName()).append('(');
      StringUtil.join(parameters, parameter -> parameter.getPresentableText(true, context), ", ", result);
      result.append(')');

      return result.toString();
    }
    final PyClass cls = as(element, PyClass.class);
    if (cls != null && PyNames.TYPES_INSTANCE_TYPE.equals(cls.getQualifiedName())) {
      return "<old-style class>";
    }
    return element.getName();
  }

  public PyMethodMember(final PyElement element) {
    super(element, trimUnderscores(buildNameFor(element)), element.getIcon(0));
    myFullName = buildNameFor(element);
  }

  public static String trimUnderscores(String s) {
    return StringUtil.trimStart(StringUtil.trimStart(s, "_"), "_");
  }

  @Override
  public MemberChooserObject getParentNodeDelegate() {
    final PyElement element = (PyElement)getPsiElement();
    final PyClass parent = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    assert (parent != null);
    return new PyMethodMember(parent);
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    component.append(myFullName, getTextAttributes(tree));
    component.setIcon(getPsiElement().getIcon(0));
  }
}
