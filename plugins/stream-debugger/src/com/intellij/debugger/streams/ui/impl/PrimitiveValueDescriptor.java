// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.memory.utils.InstanceValueDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveValueDescriptor extends InstanceValueDescriptor {
  PrimitiveValueDescriptor(@NotNull Project project, @Nullable Value value) {
    super(project, value);
  }

  @Override
  public String calcValueName() {
    final Value value = getValue();
    if (value == null) {
      return "value";
    }
    if (value instanceof ObjectReference) {
      return super.calcValueName();
    }

    return value.type().name();
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
    final Value value = getValue();
    if (value instanceof ObjectReference) {
      return super.getDescriptorEvaluation(debuggerContext);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    return elementFactory.createExpressionFromText(value.toString(), ContextUtil.getContextElement(debuggerContext));
  }
}
