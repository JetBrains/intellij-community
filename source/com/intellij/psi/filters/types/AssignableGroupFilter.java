package com.intellij.psi.filters.types;

import com.intellij.psi.filters.FalseFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.InitializableFilter;
import com.intellij.psi.filters.classes.InheritorFilter;
import com.intellij.psi.filters.element.AssignableFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;

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
    for(int i = 0; i < classes.length; i++){
      if (classes[i] instanceof PsiClass) {
        addFilter(new InheritorFilter((PsiClass)classes[i]));
      }
      if (classes[i] instanceof PsiType) {
        addFilter(new AssignableFromFilter((PsiType)classes[i]));
      }
    }
    addFilter(new FalseFilter());
  }
}