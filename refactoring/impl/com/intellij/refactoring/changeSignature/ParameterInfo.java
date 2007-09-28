/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class ParameterInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ParameterInfo");
  public final int oldParameterIndex;
  boolean useAnySingleVariable;
  private String name = "";
  private CanonicalTypes.Type myType;

  String defaultValue = "";

  public ParameterInfo(int oldParameterIndex) {
    this.oldParameterIndex = oldParameterIndex;
  }

  public ParameterInfo(int oldParameterIndex, @NonNls String name, PsiType aType) {
    setName(name);
    this.oldParameterIndex = oldParameterIndex;
    setType(aType);
  }

  public ParameterInfo(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue) {
    this(oldParameterIndex, name, aType, defaultValue, false);
  }

  public ParameterInfo(int oldParameterIndex, @NonNls String name, PsiType aType, @NonNls String defaultValue, boolean useAnyVariable) {
    this(oldParameterIndex, name, aType);
    this.defaultValue = defaultValue;
    useAnySingleVariable = useAnyVariable;
  }

  public void setUseAnySingleVariable(boolean useAnySingleVariable) {
    this.useAnySingleVariable = useAnySingleVariable;
  }

  public void updateFromMethod(PsiMethod method) {
    if (getTypeWrapper() != null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(oldParameterIndex >= 0 && oldParameterIndex < parameters.length);
    final PsiParameter parameter = parameters[oldParameterIndex];
    setName(parameter.getName());
    setType(parameter.getType());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ParameterInfo)) return false;

    ParameterInfo parameterInfo = (ParameterInfo) o;

    if (oldParameterIndex != parameterInfo.oldParameterIndex) return false;
    if (defaultValue != null ? !defaultValue.equals(parameterInfo.defaultValue) : parameterInfo.defaultValue != null) return false;
    if (!getName().equals(parameterInfo.getName())) return false;
    if (!getTypeText().equals(parameterInfo.getTypeText())) return false;

    return true;
  }

  public int hashCode() {
    int result = getName().hashCode();
    result = 29 * result + getTypeText().hashCode();
    return result;
  }

  public String getTypeText() {
    if (getTypeWrapper() != null) {
      return getTypeWrapper().getTypeText();
    }
    else {
      return "";
    }
  }

  PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (getTypeWrapper() != null) {
      return getTypeWrapper().getType(context, manager);
    } else {
      return null;
    }
  }

  void setType(PsiType type) {
    myType = CanonicalTypes.createTypeWrapper(type);
  }

  public String getName() {
    return name;
  }

  public CanonicalTypes.Type getTypeWrapper() {
    return myType;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isVarargType() {
    return getTypeText().endsWith("...");
  }

  public static ParameterInfo[] fromMethod(PsiMethod method) {
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      result.add(new ParameterInfo(i, parameter.getName(), parameter.getType()));
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }
}
