/*
 * Class MethodEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class MethodEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.MethodEvaluator");
  private JVMName myClassName;
  private JVMName myMethodSignature;
  private String myMethodName;
  private List myArgumentEvaluators;
  private Evaluator myObjectEvaluator;

  public MethodEvaluator(Evaluator objectEvaluator, JVMName className, String methodName, JVMName signature, List argumentEvaluators) {
    myObjectEvaluator = objectEvaluator;
    myClassName = className;
    myMethodName = methodName;
    myMethodSignature = signature;
    myArgumentEvaluators = argumentEvaluators;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if(!context.getDebugProcess().isAttached()) return null;
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Object object = myObjectEvaluator.evaluate(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("MethodEvaluator: object = " + object);
    }
    if(object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }
    if (!(object instanceof ObjectReference || object instanceof ClassType)) {
      throw EvaluateExceptionUtil.createEvaluateException("Error evaluating method : " + myMethodName);
    }
    List args = new ArrayList(myArgumentEvaluators.size());
    for (Iterator it = myArgumentEvaluators.iterator(); it.hasNext();) {
      Evaluator evaluator = (Evaluator)it.next();
      args.add(evaluator.evaluate(context));
    }
    try {
      String className = myClassName.getName(debugProcess);
      ReferenceType referenceType;

      if(object instanceof ObjectReference) {
        referenceType = (ReferenceType)DebuggerUtilsEx.getSuperType(((ObjectReference)object).referenceType(), className);
      } else if(object instanceof ClassType) {
        referenceType = (ReferenceType)DebuggerUtilsEx.getSuperType((ClassType)object, className);
      } else {
        referenceType = debugProcess.findClass(context, className, context.getClassLoader());
      }

      if (object instanceof ClassType) {
        if(referenceType instanceof ClassType) {
          Method jdiMethod;
          if(myMethodSignature != null) {
            jdiMethod = ((ClassType)referenceType).concreteMethodByName(myMethodName, myMethodSignature.getName(debugProcess));
          } else {
            List list = referenceType.methodsByName(myMethodName);
            jdiMethod = (Method)(list.size() > 0 ? list.get(0) : null);
          }
          if (jdiMethod != null && jdiMethod.isStatic()) {
            return debugProcess.invokeMethod(context, (ClassType)referenceType, jdiMethod, args);
          }
        }
        throw EvaluateExceptionUtil.createEvaluateException("No such static method: " + DebuggerUtilsEx.methodName(referenceType.name(), myMethodName, myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null));
      }
      // object should be ObjectReference
      ObjectReference objRef = (ObjectReference)object;
      Method jdiMethod = DebuggerUtilsEx.findMethod(referenceType, myMethodName, myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null);
      if (jdiMethod == null) {
        throw EvaluateExceptionUtil.createEvaluateException("No such non-static method: " + DebuggerUtilsEx.methodName(referenceType.name(), myMethodName, myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null));
      }
      return debugProcess.invokeMethod(context, objRef, jdiMethod, args);
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }
}
