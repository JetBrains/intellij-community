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
      return "null";
    }
    if (value instanceof ObjectReference) {
      return super.calcValueName();
    }

    return value.type().name();
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
    if (getValue() instanceof ObjectReference) {
      return super.getDescriptorEvaluation(debuggerContext);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    return elementFactory.createExpressionFromText("", ContextUtil.getContextElement(debuggerContext));
  }
}
