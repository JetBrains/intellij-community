package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;

import java.util.Iterator;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ToStringRenderer extends NodeRendererImpl {
  public static final String UNIQUE_ID = "ToStringRenderer";
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ToStringRenderer");

  public ToStringRenderer() {
    setEnabled(true);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public String getName() {
    return "toString";
  }

  public void setName(String name) {
    // prohibit change
  }

  public ToStringRenderer clone() {
    return (ToStringRenderer)super.clone();
  }

  public String calcLabel(final ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, final DescriptorLabelListener labelListener)
    throws EvaluateException {
    Value value = valueDescriptor.getValue();
    BatchEvaluator.getBatchEvaluator(evaluationContext.getDebugProcess()).invoke(new ToStringCommand(evaluationContext, value) {
      public void evaluationResult(String message) {
        valueDescriptor.setValueLabel(message != null ? "\"" + message + "\"" : "");
        labelListener.labelChanged();
      }

      public void evaluationError(String message) {
        valueDescriptor.setValueLabelFailed(new EvaluateException(message + " Failed to evaluate toString() for this object", null));
        labelListener.labelChanged();
      }
    });
    return NodeDescriptor.EVALUATING_MESSAGE;
  }

  public boolean isApplicable(Type type) {
    if(!(type instanceof ReferenceType)) {
      return false;
    }
    if(type.name().equals("java.lang.String")) {
      return false;
    }
    if(!overridesToString(type)) {
      return false;
    }
    return true;
  }

  boolean overridesToString(Type type) {
    if(type instanceof ClassType) {
      ClassType classType = (ClassType)type;
      java.util.List list = classType.methodsByName("toString", "()Ljava/lang/String;");
      for (Iterator iterator = list.iterator(); iterator.hasNext();) {
        Method method = (Method)iterator.next();
        if(!(method.declaringType().name()).equals("java.lang.Object")){
          return true;
        }
      }
    }
    return false;
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    getDefaultRenderer(value, evaluationContext).buildChildren(value, builder, evaluationContext);
  }

  private static NodeRenderer getDefaultRenderer(Value value, StackFrameContext context) {
    Type type = value != null ? value.type() : null;
    return ((DebugProcessImpl)context.getDebugProcess()).getDefaultRenderer(type);
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return getDefaultRenderer(((ValueDescriptor) node.getDescriptor()).getValue(), context).getChildValueExpression(node, context);
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return getDefaultRenderer(value, evaluationContext).isExpandable(value, evaluationContext, parentDescriptor);
  }

}
