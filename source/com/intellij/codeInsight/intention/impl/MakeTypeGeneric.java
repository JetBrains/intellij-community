package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.JavaTokenType;

/**
 *  @author dsl
 */
public class MakeTypeGeneric implements IntentionAction {
  private PsiVariable myVariable;
  private PsiType myNewVariableType;

  public String getFamilyName() {
    return "Make Type Generic";
  }

  public String getText() {
    return "Change type of " + myVariable.getName() + " to " + myNewVariableType.getPresentableText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!file.isWritable()) return false;
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    myVariable = null;
    myNewVariableType = null;
    if (element instanceof PsiIdentifier) {
      if (element.getParent() instanceof PsiVariable) {
        myVariable = (PsiVariable)element.getParent();
      }
    } else if (element instanceof PsiJavaToken ) {
      final PsiJavaToken token = (PsiJavaToken) element;
      if (token.getTokenType() != JavaTokenType.EQ) return false;
      if (token.getParent() instanceof PsiVariable) {
        myVariable = (PsiVariable) token.getParent();
      }
    }
    if (myVariable == null) return false;
    final PsiExpression initializer = myVariable.getInitializer();
    if (initializer == null) return false;
    final PsiType variableType = myVariable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return false;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return false;
    if (!(initializerType instanceof PsiClassType)) return false;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return false;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return false;
    final PsiSubstitutor targetSubstitutor = TypeConversionUtil.getClassSubstitutor(variableResolveResult.getElement(), initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return false;
    myNewVariableType = myVariable.getManager().getElementFactory().createType(variableResolveResult.getElement(), targetSubstitutor);
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myVariable.getTypeElement().replace(myVariable.getManager().getElementFactory().createTypeElement(myNewVariableType));
  }

  public boolean startInWriteAction() {
    return true;
  }

}
