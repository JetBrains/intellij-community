package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.BooleanValue;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 5:03:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class IfStatementEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;

  private Modifier myModifier;

  public IfStatementEvaluator(Evaluator conditionEvaluator, Evaluator thenEvaluator, Evaluator elseEvaluator) {
    myConditionEvaluator = conditionEvaluator;
    myThenEvaluator = thenEvaluator;
    myElseEvaluator = elseEvaluator;
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = myConditionEvaluator.evaluate(context);
    if(!(value instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
    } else {
      if(((BooleanValue)value).booleanValue()) {
        value = myThenEvaluator.evaluate(context);
        myModifier = myThenEvaluator.getModifier();
      }
      else {
        if(myElseEvaluator != null) {
          value = myElseEvaluator.evaluate(context);
          myModifier = myElseEvaluator.getModifier();
        } else {
          value = context.getDebugProcess().getVirtualMachineProxy().mirrorOf();
          myModifier = null;
        }
      }
    }
    return value;
  }

}
