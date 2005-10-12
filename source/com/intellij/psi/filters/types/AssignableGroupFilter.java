package com.intellij.psi.filters.types;

import com.intellij.psi.filters.FalseFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.InitializableFilter;
import com.intellij.psi.filters.classes.InheritorFilter;
import com.intellij.psi.filters.element.AssignableFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.PsiImmediateClassType;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:27:25
 * To change this template use Options | File Templates.
 */
public class AssignableGroupFilter extends OrFilter implements InitializableFilter{
  public AssignableGroupFilter(){}

  public AssignableGroupFilter(PsiClass[] classes){
    init(classes);
  }

  public void init(Object[] classes){
    for (Object aClass : classes) {
      if (aClass instanceof PsiClass) {
        addFilter(new AssignableFromFilter(new PsiImmediateClassType((PsiClass)aClass, PsiSubstitutor.EMPTY)));
      }
      if (aClass instanceof PsiType) {
        addFilter(new AssignableFromFilter((PsiType)aClass));
      }
    }
    addFilter(new FalseFilter());
  }
}