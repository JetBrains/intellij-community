/*
 * Class WatchItemDescriptor
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.sun.jdi.Value;

/**
 * update(Value, boolean) method must be called whenever the state of the target VM changes
 */
public class WatchItemDescriptor extends EvaluationDescriptor {
  private boolean myAllowBreakpoints;

  public WatchItemDescriptor(Project project, TextWithImportsImpl text, boolean allowBreakpoints) {
    super(text, project);
    setValueLabel("");
    myAllowBreakpoints = allowBreakpoints;
  }

  public WatchItemDescriptor(Project project, TextWithImportsImpl text, Value value, boolean allowBreakpoints) {
    super(text, project, value);
    setValueLabel("");
    myAllowBreakpoints = allowBreakpoints;
  }

  public String getName() {
    return ((TextWithImportsImpl)getEvaluationText()).getText();
  }

  public void setNew() {
    myIsNew = true;
  }

  // call update() after setting a new expression
  public void setEvaluationText(TextWithImportsImpl evaluationText) {
    if (!Comparing.equal(getEvaluationText(), evaluationText)) {
      setLvalue(false);
    }
    myText = evaluationText;
    myIsNew = true;
    setValueLabel("");
  }

  protected EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext) {
    evaluationContext.setAllowBreakpoints(myAllowBreakpoints);
    return evaluationContext;
  }

  protected PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException {
    final PsiElement psiContext = PositionUtil.getContextElement(context);
    return getEvaluationText().createCodeFragment(psiContext, myProject);
  }

  public void setAllowBreakpoints(boolean b) {
    myAllowBreakpoints = b;
  }
}