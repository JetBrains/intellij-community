/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:32:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class DfaTypeValue extends DfaValue {
  public static class Factory {
    private static volatile Factory ourInstance;
    private final DfaTypeValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaTypeValue>> myStringToObject;

    private Factory() {
      mySharedInstance = new DfaTypeValue();
      myStringToObject = new HashMap<String, ArrayList<DfaTypeValue>>();
    }

    public static Factory getInstance() {
      if (ourInstance == null) {
        ourInstance = new Factory();
      }
      return ourInstance;
    }

    public static void freeInstance() {
      ourInstance = null;
    }

    public DfaTypeValue create(PsiType myType) {
      mySharedInstance.myType = myType;
      mySharedInstance.myCanonicalText = myType.getCanonicalText();
      if (mySharedInstance.myCanonicalText == null) {
        mySharedInstance.myCanonicalText = "null";
      }

      String id = mySharedInstance.toString();
      ArrayList<DfaTypeValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaTypeValue>();
        myStringToObject.put(id, conditions);
      } else {
        for (int i = 0; i < conditions.size(); i++) {
          DfaTypeValue aType = conditions.get(i);
          if (aType.hardEquals(mySharedInstance)) return aType;
        }
      }

      DfaTypeValue result = new DfaTypeValue(myType);
      conditions.add(result);
      return result;
    }
  }

  private PsiType myType;
  private String myCanonicalText;

  private DfaTypeValue() {
  }

  private DfaTypeValue(PsiType type) {
    myType = type;
    myCanonicalText = type.getCanonicalText();
    if (myCanonicalText == null) {
      myCanonicalText = "null";
    }
  }

  public PsiType getType() {
    return myType;
  }

  public String toString() {
    return myCanonicalText;
  }

  private boolean hardEquals(DfaTypeValue aType) {
    return aType.toString().equals(toString());
  }

  public boolean isAssignableFrom(DfaTypeValue dfaType) {
    if (dfaType == null) return false;
    return myType.isAssignableFrom(dfaType.myType);
  }

  public boolean isConvertibleFrom(DfaTypeValue dfaType) {
    if (dfaType == null) return false;
    return myType.isConvertibleFrom(dfaType.myType);
  }
}
