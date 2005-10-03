/*
 * Class ThisEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.DebuggerBundle;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

import java.util.Iterator;
import java.util.List;

public class ThisEvaluator implements Evaluator {
  private int myIterations;

  public ThisEvaluator() {
    myIterations = 0;
  }

  public ThisEvaluator(int iterations) {
    myIterations = iterations;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value objRef = context.getThisObject();
    if(myIterations > 0) {
      ObjectReference thisRef = (ObjectReference)objRef;
      for (int idx = 0; idx < myIterations && thisRef != null; idx++) {
        thisRef  = getOuterObject(thisRef);
      }
      objRef = thisRef;
    }
    if(objRef == null) throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.this.not.avalilable"));
    return objRef;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static ObjectReference getOuterObject(ObjectReference objRef) {
    if (objRef == null) return null;
    List list = objRef.referenceType().fields();
    for (Iterator it = list.iterator(); it.hasNext();) {
      Field field = (Field)it.next();
      String name = field.name();
      if (name != null && name.startsWith("this$")) {
        ObjectReference rv = (ObjectReference)objRef.getValue(field);
        if (rv != null) {
          return rv;
        }
      }
    }
    return null;
  }
}
