/*
 * Class ConditionalExpressionEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Value;

class ConditionalExpressionEvaluator implements Evaluator {
  private Evaluator myConditionEvaluator;
  private Evaluator myThenEvaluator;
  private Evaluator myElseEvaluator;

  public ConditionalExpressionEvaluator(Evaluator conditionEvaluator, Evaluator thenEvaluator, Evaluator elseEvaluator) {
    myConditionEvaluator = conditionEvaluator;
    myThenEvaluator = thenEvaluator;
    myElseEvaluator = elseEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value condition = (Value)myConditionEvaluator.evaluate(context);
    if (condition == null || !(condition instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.createEvaluateException("Condition is not of type 'boolean'");
    }
    return ((BooleanValue)condition).booleanValue()? myThenEvaluator.evaluate(context) : myElseEvaluator.evaluate(context);
  }
}
