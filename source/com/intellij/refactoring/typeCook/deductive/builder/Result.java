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
  private final Settings mySettings;
  private final HashSet<PsiTypeCastExpression> myCasts;

  private int myCookedNumber = -1;
  private int myCastsRemoved = -1;

  private Binding myBinding;

  public Result(final System system) {
    myVictims = system.myElements;
    myTypes = system.myTypes;
    mySettings = system.mySettings;
    myCasts = system.myCasts;
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
    final PsiType originalType = Util.getType(element);

    if (myBinding != null) {
      final PsiType type = myBinding.substitute(myTypes.get(element));

      if (type == null && originalType.getCanonicalText().equals("java.lang.Object")){
        return originalType;
      }

      return type;
    }

    return originalType;
  }

  public HashSet<PsiElement> getCookedElements() {
    myCookedNumber = 0;

    final HashSet<PsiElement> set = new HashSet<PsiElement>();

    for (final Iterator<PsiElement> e = myVictims.iterator(); e.hasNext();) {
      final PsiElement element = e.next();
      final PsiType originalType = Util.getType(element);

      final PsiType cookedType = getCookedType(element);
      if (cookedType != null && !originalType.equals(cookedType)) {
        set.add(element);
        myCookedNumber++;
      }
    }

    return set;
  }

  public void apply(final HashSet<PsiElement> victims) {
    for (final Iterator<PsiElement> e = victims.iterator(); e.hasNext();) {
      final PsiElement element = e.next();

      Util.changeType(element, getCookedType(element));
    }

    if (mySettings.dropObsoleteCasts()) {
      myCastsRemoved = 0;

      for (final Iterator<PsiTypeCastExpression> c = myCasts.iterator(); c.hasNext();) {
        final PsiTypeCastExpression cast = c.next();

        if (cast.getType().equals(cast.getOperand().getType())) {
          try {
            cast.replace(cast.getOperand());
            myCastsRemoved++;
          }
          catch (IncorrectOperationException e1) {
            LOG.error(e1);
          }
        }
      }
    }
  }

  private String getRatio(final int x, final int y) {
    return x != -1 ? x + " of " + y + (y != 0 ? " (" + (x * 100 / y) + "%)" : "") : "not calculated";
  }

  public String getReport() {
    return "Items cooked : " + getRatio(myCookedNumber, myVictims.size()) + "\n" +
           "Casts removed: " + getRatio(myCastsRemoved, myCasts.size()) + "\n";
  }
}
