/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:23 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;

public class DfaConstValue extends DfaValue {
  public static class Factory {
    private DfaConstValue dfaNull;
    private DfaConstValue dfaFalse;
    private DfaConstValue dfaTrue;


    private static volatile Factory myInstance;
    private final HashMap<Object, DfaConstValue> myValues;

    private Factory() {
      myValues = new HashMap<Object, DfaConstValue>();
      dfaNull = new DfaConstValue(null);
      dfaFalse = new DfaConstValue(Boolean.FALSE);
      dfaTrue = new DfaConstValue(Boolean.TRUE);
    }

    public static Factory getInstance() {
      if (myInstance == null) {
        myInstance = new Factory();
      }
      return myInstance;
    }

    public static void freeInstance() {
      myInstance = null;
    }

    public DfaConstValue create(PsiLiteralExpression expr) {
      if (expr.getType() == PsiType.NULL) return dfaNull;
      Object value = expr.getValue();
      if (value == null) return null;
      return createFromValue(value);
    }

    public DfaConstValue create(PsiVariable variable) {
      Object value = variable.computeConstantValue();
      if (value == null) return null;
      return createFromValue(value);
    }

    private DfaConstValue createFromValue(Object value) {
      if (value == Boolean.TRUE) return dfaTrue;
      if (value == Boolean.FALSE) return dfaFalse;

      DfaConstValue instance = myValues.get(value);
      if (instance == null) {
        instance = new DfaConstValue(value);
        myValues.put(value, instance);
      }

      return instance;
    }

    public DfaConstValue getFalse() {
      return dfaFalse;
    }

    public DfaConstValue getTrue() {
      return dfaTrue;
    }

    public DfaConstValue getNull() {
      return dfaNull;
    }
  }

  private Object myValue;

  private DfaConstValue(Object value) {
    myValue = value;
  }

  public String toString() {
    if (myValue == null) return "null";
    return myValue.toString();
  }

  public DfaValue createNegated() {
    if (this == Factory.getInstance().getTrue()) return Factory.getInstance().getFalse();
    if (this == Factory.getInstance().getFalse()) return Factory.getInstance().getTrue();
    return DfaUnknownValue.getInstance();
  }
}
