package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 5:24:17 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class EvaluationDescriptor extends ValueDescriptorImpl{
  private Modifier myModifier;
  protected TextWithImportsImpl myText;

  protected EvaluationDescriptor(TextWithImportsImpl text, Project project, Value value) {
    super(project, value);
    myText = text;
  }

  protected EvaluationDescriptor(TextWithImportsImpl text, Project project) {
    super(project);
    setLvalue(false);
    myText = text;
  }

  protected abstract EvaluationContextImpl getEvaluationContext (EvaluationContextImpl evaluationContext);
  protected abstract PsiCodeFragment       getEvaluationCode    (StackFrameContext context) throws EvaluateException;

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    try {
      final EvaluationContextImpl thisEvaluationContext = getEvaluationContext(evaluationContext);

      final ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject, new EvaluatingComputable<ExpressionEvaluator>() {
        public ExpressionEvaluator compute() throws EvaluateException {
          return EvaluatorBuilderImpl.getInstance().build(getEvaluationCode(thisEvaluationContext));
        }
      });


      if(!thisEvaluationContext.getDebugProcess().isAttached()) throw EvaluateExceptionUtil.PROCESS_EXITED;
      StackFrameProxyImpl frameProxy = thisEvaluationContext.getFrameProxy();
      if (frameProxy == null) throw EvaluateExceptionUtil.NULL_STACK_FRAME;

      evaluator.evaluate(thisEvaluationContext);

      setLvalue(evaluator.getModifier() != null);
      myModifier = evaluator.getModifier();
      return evaluator.getValue();
    } catch (final EvaluateException ex) {
      throw new EvaluateException(ex.getMessage() + " Failed to evaluate expression", ex);
    }
  }

  public String calcValueName() {
    return getName();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElement evaluationCode = getEvaluationCode(context);
    if(evaluationCode instanceof PsiExpressionCodeFragment) {
      return ((PsiExpressionCodeFragment)evaluationCode).getExpression();
    } else {
      throw new EvaluateException("Cannot create expression from code fragment.", null); 
    }
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public boolean canSetValue() {
    return super.canSetValue() && myModifier != null && myModifier.canSetValue();
  }

  public TextWithImports getEvaluationText() {
    return myText;
  }
}
