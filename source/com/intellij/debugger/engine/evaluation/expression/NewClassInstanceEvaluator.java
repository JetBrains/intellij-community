/**
 * class NewArrayInstanceEvaluator
 * created Jun 27, 2001
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class NewClassInstanceEvaluator implements Evaluator {
  private Evaluator myClassTypeEvaluator;
  private JVMName myConstructorSignature;
  private Evaluator[] myParamsEvaluators;

  public NewClassInstanceEvaluator(Evaluator classTypeEvaluator, JVMName constructorSignature, Evaluator[] argumentEvaluators) {
    myClassTypeEvaluator = classTypeEvaluator;
    myConstructorSignature = constructorSignature;
    myParamsEvaluators = argumentEvaluators;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object obj = myClassTypeEvaluator.evaluate(context);
    if (!(obj instanceof ClassType)) {
      throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate class type");
    }
    ClassType classType = (ClassType)obj;
    // find constructor
    Method method = DebuggerUtilsEx.findMethod(classType, "<init>", myConstructorSignature.getName(debugProcess));
    if (method == null) {
      throw EvaluateExceptionUtil.createEvaluateException("Cannot find constructor for class " + classType.name());
    }
    // evaluate arguments
    List arguments;
    if (myParamsEvaluators != null) {
      arguments = new ArrayList(myParamsEvaluators.length);
      for (int idx = 0; idx < myParamsEvaluators.length; idx++) {
        Evaluator evaluator = myParamsEvaluators[idx];
        arguments.add(evaluator.evaluate(context));
      }
    }
    else {
      arguments = Collections.EMPTY_LIST;
    }
    ObjectReference objRef;
    try {
      objRef = debugProcess.newInstance(context, classType, method, arguments);
    }
    catch (EvaluateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    return objRef;
  }

  public Modifier getModifier() {
    return null;
  }
}
