package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.*;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.Bottom;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.openapi.project.Project;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Feb 7, 2005
 * Time: 7:40:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class Result {
  final HashSet<PsiElement> myVictims;
  final HashMap<PsiElement, PsiType> myTypes;
  final Project myProject;

  Binding myBinding;

  public Result(final System system) {
    myVictims = system.myElements;
    myTypes = system.myTypes;
    myProject = system.myProject;
  }

  final private PsiType substitute(final PsiType t) {
    if (t instanceof PsiWildcardType) {
      final PsiWildcardType wcType = (PsiWildcardType)t;
      final PsiType bound = wcType.getBound();

      if (bound == null) {
        return t;
      }

      final PsiManager manager = PsiManager.getInstance(myProject);
      final PsiType subst = substitute(bound);
      return wcType.isExtends() ? PsiWildcardType.createExtends(manager, subst) : PsiWildcardType.createSuper(manager, subst);
    }
    else if (t instanceof PsiTypeVariable) {
      final PsiType b = myBinding.apply(t);

      if (b instanceof Bottom || b instanceof PsiTypeVariable) {
        return null;
      }

      return substitute(b);
    }
    else if (t instanceof Bottom) {
      return null;
    }
    else if (t instanceof PsiArrayType) {
      return substitute(((PsiArrayType)t).getComponentType()).createArrayType();
    }
    else if (t instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)t).resolveGenerics();

      final PsiClass aClass = result.getElement();
      final PsiSubstitutor aSubst = result.getSubstitutor();

      if (aClass == null) {
        return t;
      }

      PsiSubstitutor theSubst = PsiSubstitutor.EMPTY;

      for (final Iterator<PsiTypeParameter> p = aSubst.getSubstitutionMap().keySet().iterator(); p.hasNext();) {
        final PsiTypeParameter parm = p.next();
        final PsiType type = aSubst.substitute(parm);

        theSubst = theSubst.put(parm, substitute(type));
      }

      return aClass.getManager().getElementFactory().createType(aClass, theSubst);
    }
    else {
      return t;
    }
  }

  public void incorporateSolution(final Binding binding) {
    if (myBinding == null) {
      myBinding = binding;
    }
    else {
      myBinding.merge(binding);
    }
  }

  public PsiType getCookedType(final PsiElement element) {
    return substitute(myTypes.get(element));
  }

  public HashSet<PsiElement> getCookedElements() {
    final HashSet<PsiElement> set = new HashSet<PsiElement>();

    for (final Iterator<PsiElement> e = myVictims.iterator(); e.hasNext();) {
      final PsiElement element = e.next();
      final PsiType originalType =
        element instanceof PsiMethod
        ? ((PsiMethod)element).getReturnType()
        : element instanceof PsiVariable
        ? ((PsiVariable)element).getType()
        : ((PsiExpression)element).getType();

      if (!originalType.equals(getCookedType(element))){
        set.add(element);
      }
    }

    return set;
  }

  public void apply(final HashSet<PsiElement> victims) {
    for (final Iterator<PsiElement> e=victims.iterator(); e.hasNext();){
      final PsiElement element = e.next();

      Util.changeType(element, getCookedType(element));
    }
  }
}
