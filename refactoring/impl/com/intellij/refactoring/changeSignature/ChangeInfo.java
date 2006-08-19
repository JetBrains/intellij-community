/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.javaee.ejb.role.EjbMethodRole;
import com.intellij.javaee.ejb.EjbHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ChangeInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeInfo");

  final String newVisibility;
  private PsiMethod method;
  final String oldName;
  final String oldType;
  final String[] oldParameterNames;
  final String[] oldParameterTypes;
  final String newName;
  final CanonicalTypes.Type newReturnType;
  final ParameterInfo[] newParms;
  ThrownExceptionInfo[] newExceptions;
  final boolean[] toRemoveParm;
  boolean isVisibilityChanged = false;
  boolean isNameChanged = false;
  boolean isReturnTypeChanged = false;
  boolean isParameterSetOrOrderChanged = false;
  boolean isExceptionSetChanged = false;
  boolean isExceptionSetOrOrderChanged = false;
  boolean isParameterNamesChanged = false;
  boolean isParameterTypesChanged = false;
  boolean isPropagationEnabled = true;
  final boolean wasVararg;
  final boolean retainsVarargs;
  final boolean obtainsVarags;
  PsiIdentifier newNameIdentifier;
  PsiType newTypeElement;
  PsiExpression[] defaultValues;
  final EjbMethodRole ejbRole;

  /**
   * @param newExceptions null if not changed
   */
  public ChangeInfo(String newVisibility,
                    PsiMethod method,
                    String newName,
                    CanonicalTypes.Type newType,
                    @NotNull ParameterInfo[] newParms,
                    ThrownExceptionInfo[] newExceptions) {
    this.newVisibility = newVisibility;
    this.method = method;
    this.newName = newName;
    newReturnType = newType;
    this.newParms = newParms;
    wasVararg = method.isVarArgs();

    oldName = method.getName();
    final PsiManager manager = method.getManager();
    if (!method.isConstructor()){
      oldType = manager.getElementFactory().createTypeElement(method.getReturnType()).getText();
    }
    else{
      oldType = null;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    oldParameterNames = new String[parameters.length];
    oldParameterTypes = new String[parameters.length];
    for(int i = 0; i < parameters.length; i++){
      PsiParameter parameter = parameters[i];
      oldParameterNames[i] = parameter.getName();
      oldParameterTypes[i] = parameter.getManager().getElementFactory().createTypeElement(parameter.getType()).getText();
    }

    isVisibilityChanged = !method.hasModifierProperty(newVisibility);

    isNameChanged = !newName.equals(oldName);
    if (!method.isConstructor()){
      try {
        isReturnTypeChanged = !newReturnType.getType(this.method, manager).equals(this.method.getReturnType());
      }
      catch (IncorrectOperationException e) {
        isReturnTypeChanged = true;
      }
    }
    if (parameters.length != newParms.length){
      isParameterSetOrOrderChanged = true;
    }
    else {
      for(int i = 0; i < newParms.length; i++){
        ParameterInfo parmInfo = newParms[i];
        PsiParameter parameter = parameters[i];
        if (i != parmInfo.oldParameterIndex){
          isParameterSetOrOrderChanged = true;
          break;
        }
        if (!parmInfo.getName().equals(parameter.getName())){
          isParameterNamesChanged = true;
        }
        try {
          if (!parmInfo.createType(method, manager).equals(parameter.getType())){
            isParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          isParameterTypesChanged = true;
        }
      }
    }

    setupPropagationEnabled(parameters, newParms);

    setupExceptions(newExceptions, method);

    toRemoveParm = new boolean[parameters.length];
    Arrays.fill(toRemoveParm, true);
    for (ParameterInfo info : newParms) {
      if (info.oldParameterIndex < 0) continue;
      toRemoveParm[info.oldParameterIndex] = false;
    }

    PsiElementFactory factory = manager.getElementFactory();
    defaultValues = new PsiExpression[newParms.length];
    for(int i = 0; i < newParms.length; i++){
      ParameterInfo info = newParms[i];
      if (info.oldParameterIndex < 0 && !info.isVarargType()){
        try{
          defaultValues[i] = factory.createExpressionFromText(info.defaultValue, method);
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
    }

    if (this.newParms.length == 0) {
      retainsVarargs = false;
      obtainsVarags = false;
    }
    else {
      final ParameterInfo lastNewParm = this.newParms[this.newParms.length - 1];
      obtainsVarags = lastNewParm.isVarargType();
      retainsVarargs = lastNewParm.oldParameterIndex >= 0 && obtainsVarags;
    }

    ejbRole = EjbHelper.getEjbHelper().getEjbRole(method);
  }

  private void setupExceptions(ThrownExceptionInfo[] newExceptions, final PsiMethod method) {
    if (newExceptions == null) newExceptions = extractExceptions(method);

    this.newExceptions = newExceptions;

    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    isExceptionSetChanged = newExceptions.length != types.length;
    if (!isExceptionSetChanged) {
      for (int i = 0; i < newExceptions.length; i++) {
        try {
          if (newExceptions[i].oldIndex < 0 || !types[i].equals(newExceptions[i].myType.getType(method, method.getManager()))) {
            isExceptionSetChanged = true;
            break;
          }
        }
        catch (IncorrectOperationException e) {
          isExceptionSetChanged = true;
        }
        if (newExceptions[i].oldIndex != i) isExceptionSetOrOrderChanged = true;
      }
    }

    isExceptionSetOrOrderChanged |= isExceptionSetChanged;
  }

  private void setupPropagationEnabled(final PsiParameter[] parameters, final ParameterInfo[] newParms) {
    if (parameters.length >= newParms.length) {
      isPropagationEnabled = false;
    }
    else {
      for (int i = 0; i < parameters.length; i++) {
        final ParameterInfo newParm = newParms[i];
        if (newParm.oldParameterIndex != i) {
          isPropagationEnabled = false;
          break;
        }
      }
    }
  }

  //create identity mapping
  private static ThrownExceptionInfo[] extractExceptions(PsiMethod method) {
    PsiClassType[] types = method.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] result = new ThrownExceptionInfo[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new ThrownExceptionInfo(i, types[i]);
    }
    return result;
  }

  public PsiMethod getMethod() {
    return method;
  }

  public void updateMethod(PsiMethod method) {
    this.method = method;
  }

  public ParameterInfo[] getCreatedParmsInfoWithoutVarargs() {
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    for (ParameterInfo newParm : newParms) {
      if (newParm.oldParameterIndex < 0 && !newParm.isVarargType()) {
        result.add(newParm);
      }
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }
}
