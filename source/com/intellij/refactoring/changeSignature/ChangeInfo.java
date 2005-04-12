/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

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
                    ParameterInfo[] newParms,
                    ThrownExceptionInfo[] newExceptions) {
    this.newVisibility = newVisibility;
    this.method = method;
    this.newName = newName;
    this.newReturnType = newType;
    this.newParms = newParms;
    wasVararg = method.isVarArgs();

    this.oldName = method.getName();
    final PsiManager manager = method.getManager();
    if (!method.isConstructor()){
      this.oldType = manager.getElementFactory().createTypeElement(method.getReturnType()).getText();
    }
    else{
      this.oldType = null;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    this.oldParameterNames = new String[parameters.length];
    this.oldParameterTypes = new String[parameters.length];
    for(int i = 0; i < parameters.length; i++){
      PsiParameter parameter = parameters[i];
      this.oldParameterNames[i] = parameter.getName();
      this.oldParameterTypes[i] = parameter.getManager().getElementFactory().createTypeElement(parameter.getType()).getText();
    }

    this.isVisibilityChanged = !method.hasModifierProperty(newVisibility);

    this.isNameChanged = !newName.equals(oldName);
    if (!method.isConstructor()){
      try {
        this.isReturnTypeChanged = !newReturnType.getType(this.method, manager).equals(this.method.getReturnType());
      }
      catch (IncorrectOperationException e) {
        this.isReturnTypeChanged = true;
      }
    }
    if (parameters.length != newParms.length){
      this.isParameterSetOrOrderChanged = true;
    }
    else {
      for(int i = 0; i < newParms.length; i++){
        ParameterInfo parmInfo = newParms[i];
        PsiParameter parameter = parameters[i];
        if (i != parmInfo.oldParameterIndex){
          this.isParameterSetOrOrderChanged = true;
          break;
        }
        if (!parmInfo.getName().equals(parameter.getName())){
          this.isParameterNamesChanged = true;
        }
        try {
          if (!parmInfo.createType(method, manager).equals(parameter.getType())){
            this.isParameterTypesChanged = true;
          }
        }
        catch (IncorrectOperationException e) {
          this.isParameterTypesChanged = true;
        }
      }
    }

    setupPropagationEnabled(parameters, newParms);

    setupExceptions(newExceptions, method);

    this.toRemoveParm = new boolean[parameters.length];
    Arrays.fill(this.toRemoveParm, true);
    for(int i = 0; i < newParms.length; i++){
      ParameterInfo info = newParms[i];
      if (info.oldParameterIndex < 0) continue;
      this.toRemoveParm[info.oldParameterIndex] = false;
    }

    PsiElementFactory factory = manager.getElementFactory();
    this.defaultValues = new PsiExpression[newParms.length];
    for(int i = 0; i < newParms.length; i++){
      ParameterInfo info = newParms[i];
      if (info.oldParameterIndex < 0 && !info.isVarargType()){
        try{
          this.defaultValues[i] = factory.createExpressionFromText(info.defaultValue, method);
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

    ejbRole = J2EERolesUtil.getEjbRole(method);
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
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfo newParm = newParms[i];
      if (newParm.oldParameterIndex < 0 && !newParm.isVarargType()) {
        result.add(newParm);
      }
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }
}
