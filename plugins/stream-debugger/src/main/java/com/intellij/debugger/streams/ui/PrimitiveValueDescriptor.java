package com.intellij.debugger.streams.ui;

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

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveValueDescriptor extends InstanceValueDescriptor {
  PrimitiveValueDescriptor(Project project, Value value) {
    super(project, value);
  }

  @Override
  public String calcValueName() {
    final Value value = getValue();
    if (value instanceof ObjectReference) {
      return super.calcValueName();
    }

    return value.toString();
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
    final Value value = getValue();
    if (value instanceof ObjectReference) {
      return super.getDescriptorEvaluation(debuggerContext);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    return elementFactory.createExpressionFromText("", ContextUtil.getContextElement(debuggerContext));
  }
}
