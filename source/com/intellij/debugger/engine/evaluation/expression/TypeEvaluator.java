/*
 * Class TypeEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;

class TypeEvaluator implements Evaluator {
  private JVMName myTypeName;

  public TypeEvaluator(JVMName typeName) {
    myTypeName = typeName;
  }

  public Modifier getModifier() {
    return null;
  }

  /**
   * @return ReferenceType in the target VM, with the given fully qualified name
   */
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    String typeName = myTypeName.getName(debugProcess);
    return debugProcess.findClass(context, typeName, context.getClassLoader());
  }
}
