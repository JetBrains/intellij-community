package com.intellij.psi.filters.types;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import org.jdom.Element;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:53:38
 * To change this template use Options | File Templates.
 */
public class AssignableFromFilter implements ElementFilter{
  private PsiType myType;

  public AssignableFromFilter(PsiType type){
    myType = type;
  }

  public AssignableFromFilter(){}

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null) return false;
    if (element instanceof PsiType) return myType.isAssignableFrom((PsiType) element);
    PsiSubstitutor substitutor = null;
    if(element instanceof CandidateInfo){
      final CandidateInfo info = (CandidateInfo)element;
      substitutor = info.getSubstitutor();
      element = info.getElement();
    }

    if(element instanceof PsiMethod){
      final PsiMethod method = (PsiMethod)element;
      final PsiTypeParameterList list = method.getTypeParameterList();
      if(list != null && list.getTypeParameters().length > 0){
        final PsiTypeParameter[] parameters = list.getTypeParameters();
        for (int i = 0; i < parameters.length; i++) {
          final PsiTypeParameter parameter = parameters[i];
          PsiType returnType = method.getReturnType();
          if(substitutor != null) returnType = substitutor.substitute(returnType);
          final PsiType substitutionForParameter = method.getManager().getResolveHelper().getSubstitutionForTypeParameter(parameter, returnType, myType, false);
          if(substitutionForParameter != PsiType.NULL){
            return true;
          }
        }
      }
    }
    final PsiType typeByElement = FilterUtil.getTypeByElement((PsiElement)element, context);
    if(substitutor != null) return myType.isAssignableFrom(substitutor.substitute(typeByElement));
    if(typeByElement == null)
      return false;
    return myType.isAssignableFrom(typeByElement);
  }

  public void readExternal(Element element) throws InvalidDataException{
  }

  public void writeExternal(Element element) throws WriteExternalException{
  }

  public String toString(){
    return "assignable-from(" + myType + ")";
  }
}
