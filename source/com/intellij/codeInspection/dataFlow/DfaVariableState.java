/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2002
 * Time: 9:49:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.psi.PsiPrimitiveType;

import java.util.HashSet;
import java.util.Iterator;

public class DfaVariableState implements Cloneable {
  private HashSet<DfaTypeValue> myInstanceofValues;
  private HashSet<DfaTypeValue> myNotInstanceofValues;

  public DfaVariableState() {
    myInstanceofValues = new HashSet<DfaTypeValue>();
    myNotInstanceofValues = new HashSet<DfaTypeValue>();
  }

  private boolean checkInstanceofValue(DfaTypeValue dfaType) {
    if (myInstanceofValues.contains(dfaType)) return true;

    for (Iterator iterator = myNotInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = (DfaTypeValue) iterator.next();
      if (dfaTypeValue.isAssignableFrom(dfaType)) return false;
    }

    for (Iterator iterator = myInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = (DfaTypeValue)iterator.next();
      if (!dfaType.isConvertibleFrom(dfaTypeValue)) {
        return false;
      }
    }

    return true;
  }

  public boolean setInstanceofValue(DfaTypeValue dfaType) {
    if (dfaType.getType() instanceof PsiPrimitiveType) return true;

    if (checkInstanceofValue(dfaType)) {
      myInstanceofValues.add(dfaType);
      return true;
    }

    return false;
  }

  public boolean addNotInstanceofValue(DfaTypeValue dfaType) {
    if (myNotInstanceofValues.contains(dfaType)) return true;

    for (Iterator iterator = myInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = (DfaTypeValue) iterator.next();
      if (dfaType.isAssignableFrom(dfaTypeValue)) return false;
    }

    myNotInstanceofValues.add(dfaType);
    return true;
  }

  public int hashCode() {
    return myInstanceofValues.hashCode() + myNotInstanceofValues.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaVariableState)) return false;
    DfaVariableState aState = (DfaVariableState) obj;
    return myInstanceofValues.equals(aState.myInstanceofValues) && myNotInstanceofValues.equals(aState.myNotInstanceofValues);
  }

  protected Object clone() throws CloneNotSupportedException {
    DfaVariableState newState = new DfaVariableState();

    newState.myInstanceofValues = (HashSet<DfaTypeValue>) myInstanceofValues.clone();
    newState.myNotInstanceofValues = (HashSet<DfaTypeValue>) myNotInstanceofValues.clone();

    return newState;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("instanceof {");
    for (Iterator iterator = myInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = (DfaTypeValue) iterator.next();
      buf.append(dfaTypeValue);
      if (iterator.hasNext()) buf.append(", ");
    }
    buf.append("} ");

    buf.append("not instanceof {");
    for (Iterator iterator = myNotInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = (DfaTypeValue) iterator.next();
      buf.append(dfaTypeValue);
      if (iterator.hasNext()) buf.append(", ");
    }
    buf.append("}");
    return buf.toString();
  }
}
