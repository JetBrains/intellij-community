/*
 * Class UnaryExpressionEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

class UnaryExpressionEvaluator implements Evaluator {
  private IElementType myOperationType;
  private String myExpectedType;
  private Evaluator myOperandEvaluator;

  public UnaryExpressionEvaluator(IElementType operationType, String expectedType, Evaluator operandEvaluator) {
    myOperationType = operationType;
    myExpectedType = expectedType;
    myOperandEvaluator = operandEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value operand = (Value)myOperandEvaluator.evaluate(context);
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (myOperationType == JavaTokenType.PLUS) {
      if (DebuggerUtilsEx.isNumeric(operand)) {
        return operand;
      }
      throw EvaluateExceptionUtil.createEvaluateException("Numeric expected");
    }
    else if (myOperationType == JavaTokenType.MINUS) {
      if (DebuggerUtilsEx.isNumeric(operand)) {
        double v = ((PrimitiveValue)operand).doubleValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, -v);
      }
      throw EvaluateExceptionUtil.createEvaluateException("Numeric expected");
    }
    else if (myOperationType == JavaTokenType.TILDE) {
      if (DebuggerUtilsEx.isInteger(operand)) {
        long v = ((PrimitiveValue)operand).longValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, ~v);
      }
      throw EvaluateExceptionUtil.createEvaluateException("integer expected");
    }
    else if (myOperationType == JavaTokenType.EXCL) {
      if (operand instanceof BooleanValue) {
        boolean v = ((BooleanValue)operand).booleanValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, !v);
      }
      throw EvaluateExceptionUtil.createEvaluateException("boolean expected");
    }
    else if (myOperationType == JavaTokenType.PLUSPLUS) {
      throw EvaluateExceptionUtil.createEvaluateException("Prefix operation \"++\" is not supported");
    }
    else if (myOperationType == JavaTokenType.MINUSMINUS) {
      throw EvaluateExceptionUtil.createEvaluateException("Prefix operation \"--\" is not supported");
    }
    throw EvaluateExceptionUtil.createEvaluateException("Unsupported unary expression");
  }
}
