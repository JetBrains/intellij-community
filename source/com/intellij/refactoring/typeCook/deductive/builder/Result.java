package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariable;
import com.intellij.refactoring.typeCook.deductive.PsiTypeVariableFactory;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.Bottom;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;

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
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.builder.Result");

  private final HashSet<PsiElement> myVictims;
  private final HashMap<PsiElement, PsiType> myTypes;
  private final Project myProject;
  private final Settings mySettings;
  private final PsiTypeVariableFactory myFactory;

  private Binding myBinding;

  public Result(final System system) {
    myVictims = system.myElements;
    myTypes = system.myTypes;
    myProject = system.myProject;
    mySettings = system.mySettings;
    myFactory = system.getVariableFactory();
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
      return subst instanceof  PsiWildcardType ? subst : wcType.isExtends() ? PsiWildcardType.createExtends(manager, subst) : PsiWildcardType.createSuper(manager, subst);
    }
    else if (t instanceof PsiTypeVariable) {
      if (myBinding == null) {
        return null;
      }

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
      myBinding.merge(binding, mySettings.leaveObjectParameterizedTypesRaw());
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

      final PsiType cookedType = getCookedType(element);
      if (cookedType != null && !originalType.equals(cookedType)) {
        set.add(element);
      }
    }

    return set;
  }

  public void apply(final HashSet<PsiElement> victims) {
    for (final Iterator<PsiElement> e = victims.iterator(); e.hasNext();) {
      final PsiElement element = e.next();

      Util.changeType(element, getCookedType(element));

      if (mySettings.dropObsoleteCasts() && element instanceof PsiTypeCastExpression){
        final PsiTypeCastExpression cast = ((PsiTypeCastExpression)element);

        if (cast.getType().equals(cast.getOperand().getType())){
          try {
            cast.replace(cast.getOperand());
          }
          catch (IncorrectOperationException e1) {
            LOG.error (e1);
          }
        }
      }
    }
  }
}
