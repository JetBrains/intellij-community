/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class UserExpressionDescriptorImpl extends EvaluationDescriptor implements UserExpressionDescriptor{
  private final ValueDescriptorImpl myParentDescriptor;
  private final String myTypeName;
  private final String myName;

  public UserExpressionDescriptorImpl(Project project, ValueDescriptorImpl parent, String typeName, String name, TextWithImportsImpl text) {
    super(text, project);
    myParentDescriptor = parent;
    myTypeName = typeName;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public String calcValueName() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(getName());
    buffer.append(": ");
    if(getValue() != null) buffer.append(getValue().type().name());

    return buffer.toString();
  }

  protected PsiCodeFragment getEvaluationCode(final StackFrameContext context) throws EvaluateException {
    Value value = myParentDescriptor.getValue();

    if(value instanceof ObjectReference) {
      final String typeName = value.type().name();

      PsiClass psiClass = DebuggerUtilsEx.findClass(myTypeName, myProject);

      if (psiClass == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Invalid type name " + typeName);
      }

      return getEvaluationText().createCodeFragment(psiClass, myProject);
    }
    else {
      throw EvaluateExceptionUtil.createEvaluateException("Object reference expected instead of" + myParentDescriptor.getName());
    }
  }

  public ValueDescriptorImpl getParentDescriptor() {
    return myParentDescriptor;
  }

  protected EvaluationContextImpl getEvaluationContext(final EvaluationContextImpl evaluationContext) {
    return evaluationContext.createEvaluationContext(myParentDescriptor.getValue());
  }
}