/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:45:14 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class DfaNewValue extends DfaValue {
  public static class Factory {
    private final DfaNewValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaNewValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaNewValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaNewValue>>();
    }

    public DfaValue create(PsiType type) {
      if (type == null) return DfaUnknownValue.getInstance();
      mySharedInstance.myType = type;

      String id = mySharedInstance.toString();
      ArrayList<DfaNewValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaNewValue>();
        myStringToObject.put(id, conditions);
      } else {
        for (int i = 0; i < conditions.size(); i++) {
          DfaNewValue aNew = conditions.get(i);
          if (aNew.hardEquals(mySharedInstance)) return aNew;
        }
      }

      DfaNewValue result = new DfaNewValue(type, myFactory);
      conditions.add(result);
      return result;
    }
  }

  private PsiType myType;

  private DfaNewValue(PsiType myType, DfaValueFactory factory) {
    super(factory);
    this.myType = myType;
  }

  private DfaNewValue(DfaValueFactory factory) {
    super(factory);
  }

  public String toString() {
    return "new " + myType.getCanonicalText();
  }

  public PsiType getType() {
    return myType;
  }

  private boolean hardEquals(DfaNewValue aNew) {
    return aNew.myType == myType;
  }
}
