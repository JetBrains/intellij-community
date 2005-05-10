/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2002
 * Time: 9:49:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;

public class DfaVariableState implements Cloneable {
  private HashSet<DfaTypeValue> myInstanceofValues;
  private HashSet<DfaTypeValue> myNotInstanceofValues;
  private boolean myNullable = false;
  private final boolean myVariableIsDeclaredNotNull;
  private PsiVariable myVar;

  public DfaVariableState(@Nullable PsiVariable var) {
    myVar = var;
    myInstanceofValues = new HashSet<DfaTypeValue>();
    myNotInstanceofValues = new HashSet<DfaTypeValue>();
    myNullable = var != null && AnnotationUtil.isNullable(var);
    myVariableIsDeclaredNotNull = var != null && AnnotationUtil.isNotNull(var);
  }

  public boolean isNullable() {
    return myNullable;
  }

  private boolean checkInstanceofValue(DfaTypeValue dfaType) {
    if (myInstanceofValues.contains(dfaType)) return true;

    for (DfaTypeValue dfaTypeValue : myNotInstanceofValues) {
      if (dfaTypeValue.isAssignableFrom(dfaType)) return false;
    }

    for (DfaTypeValue dfaTypeValue : myInstanceofValues) {
      if (!dfaType.isConvertibleFrom(dfaTypeValue)) return false;
    }

    return true;
  }

  public boolean setInstanceofValue(DfaTypeValue dfaType) {
    myNullable |= dfaType.isNullable();

    if (dfaType.getType() instanceof PsiPrimitiveType) return true;

    if (checkInstanceofValue(dfaType)) {
      myInstanceofValues.add(dfaType);
      return true;
    }

    return false;
  }

  public boolean addNotInstanceofValue(DfaTypeValue dfaType) {
    if (myNotInstanceofValues.contains(dfaType)) return true;

    for (DfaTypeValue dfaTypeValue : myInstanceofValues) {
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
    return myInstanceofValues.equals(aState.myInstanceofValues) &&
           myNotInstanceofValues.equals(aState.myNotInstanceofValues) &&
           myNullable == aState.myNullable;
  }

  protected Object clone() throws CloneNotSupportedException {
    DfaVariableState newState = new DfaVariableState(myVar);

    newState.myInstanceofValues = (HashSet<DfaTypeValue>) myInstanceofValues.clone();
    newState.myNotInstanceofValues = (HashSet<DfaTypeValue>) myNotInstanceofValues.clone();
    newState.myNullable = myNullable;

    return newState;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer();

    buf.append("instanceof {");
    for (Iterator<DfaTypeValue> iterator = myInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = iterator.next();
      buf.append(dfaTypeValue);
      if (iterator.hasNext()) buf.append(", ");
    }
    buf.append("} ");

    buf.append("not instanceof {");
    for (Iterator<DfaTypeValue> iterator = myNotInstanceofValues.iterator(); iterator.hasNext();) {
      DfaTypeValue dfaTypeValue = iterator.next();
      buf.append(dfaTypeValue);
      if (iterator.hasNext()) buf.append(", ");
    }
    buf.append("}");
    buf.append(", nullable=" + myNullable);
    return buf.toString();
  }

  public boolean isNotNull() {
    return myVariableIsDeclaredNotNull;
  }

  public void setNullable(final boolean nullable) {
    myNullable = nullable;
  }
}
