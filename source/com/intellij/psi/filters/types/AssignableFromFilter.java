package com.intellij.psi.filters.types;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;


/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:53:38
 * To change this template use Options | File Templates.
 */
public class AssignableFromFilter implements ElementFilter{
  private PsiType myType = null;
  private String myClassName = null;

  public AssignableFromFilter(PsiType type){
    myType = type;
  }

  public AssignableFromFilter(final String className) {
    myClassName = className;
  }

  public AssignableFromFilter(){}

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    PsiType type = myType;
    if(type == null) {
      final PsiClass aClass = context.getManager().findClass(myClassName, context.getResolveScope());
      if (aClass != null) {
        type = aClass.getManager().getElementFactory().createType(aClass, PsiSubstitutor.EMPTY);
      }
      else {
        type = null;
      }
    }
    if(type == null) return false;
    if(element == null) return false;
    if (element instanceof PsiType) return type.isAssignableFrom((PsiType) element);
    
    PsiSubstitutor substitutor = null;
    
    if(element instanceof CandidateInfo){
      final CandidateInfo info = (CandidateInfo)element;
      substitutor = info.getSubstitutor();
      element = info.getElement();
    }

    if(element instanceof PsiMethod){
      final PsiMethod method = (PsiMethod)element;
      final PsiTypeParameter[] parameters = method.getTypeParameters();
      for (final PsiTypeParameter parameter : parameters) {
        PsiType returnType = method.getReturnType();
        if (substitutor != null) returnType = substitutor.substitute(returnType);
        final PsiType substitutionForParameter = method.getManager().getResolveHelper().getSubstitutionForTypeParameter(parameter,
                                                                                                                        returnType, type,
                                                                                                                        false,
                                                                                                                        PsiUtil.getLanguageLevel(context));
        if (substitutionForParameter != PsiType.NULL) {
          return true;
        }
      }
    }
    final PsiType typeByElement = FilterUtil.getTypeByElement((PsiElement)element, context);
    if (typeByElement == null) {
      return false;
    }
    if(substitutor != null) return type.isAssignableFrom(substitutor.substitute(typeByElement));
    return type.isAssignableFrom(typeByElement);
  }

  public void readExternal(Element element) throws InvalidDataException{
  }

  public void writeExternal(Element element) throws WriteExternalException{
  }

  public String toString(){
    return "assignable-from(" + (myType != null ? myType : myClassName) + ")";
  }
}
