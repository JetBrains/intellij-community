/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class DfaVariableValue extends DfaValue {
  public static class Factory {
    private static volatile Factory myInstance;
    private final DfaVariableValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaVariableValue>> myStringToObject;

    private Factory() {
      mySharedInstance = new DfaVariableValue();
      myStringToObject = new HashMap<String, ArrayList<DfaVariableValue>>();
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

    public DfaVariableValue create(PsiVariable myVariable, boolean isNegated) {
      mySharedInstance.myVariable = myVariable;
      mySharedInstance.myIsNegated = isNegated;

      String id = mySharedInstance.toString();
      ArrayList<DfaVariableValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaVariableValue>();
        myStringToObject.put(id, conditions);
      } else {
        for (int i = 0; i < conditions.size(); i++) {
          DfaVariableValue aVar = conditions.get(i);
          if (aVar.hardEquals(mySharedInstance)) return aVar;
        }
      }

      DfaVariableValue result = new DfaVariableValue(myVariable, isNegated);
      conditions.add(result);
      return result;
    }
  }

  private PsiVariable myVariable;
  private boolean myIsNegated;

  private DfaVariableValue(PsiVariable variable, boolean isNegated) {
    myVariable = variable;
    myIsNegated = isNegated;
  }

  private DfaVariableValue() {
    myVariable = null;
    myIsNegated = false;
  }

  public PsiVariable getPsiVariable() {
    return myVariable;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  public DfaValue createNegated() {
    return Factory.getInstance().create(getPsiVariable(), !myIsNegated);
  }

  public String toString() {
    if (myVariable == null) return "$currentException";
    return (myIsNegated ? "!" : "") + myVariable.getName();
  }

  private boolean hardEquals(DfaVariableValue aVar) {
    return aVar.myVariable == myVariable && aVar.myIsNegated == myIsNegated;
  }
}
