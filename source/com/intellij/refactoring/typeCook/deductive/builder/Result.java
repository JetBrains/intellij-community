package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;

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
  private final int myCastsNumber;

  private Binding myBinding;

  public Result(final System system) {
    myVictims = system.myElements;
    myTypes = system.myTypes;
    mySettings = system.mySettings;
    myCasts = system.myCasts;
    myCastsNumber = myCasts.size();
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

      @NonNls final String objectFQName = "java.lang.Object";
      if (originalType.getCanonicalText().equals(objectFQName)) {
        if (type == null) {
          return originalType;
        }

        if (type instanceof PsiWildcardType){
          final PsiType bound = ((PsiWildcardType)type).getBound();

          if (bound != null){
            return bound;
          }

          return originalType;
        }
      }

      return type;
    }

    return originalType;
  }

  public HashSet<PsiElement> getCookedElements() {
    myCookedNumber = 0;

    final HashSet<PsiElement> set = new HashSet<PsiElement>();

    for (final PsiElement element : myVictims) {
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
    for (final PsiElement element : victims) {
      Util.changeType(element, getCookedType(element));
    }

    if (mySettings.dropObsoleteCasts()) {
      myCastsRemoved = 0;

      while (myCasts.size() > 0) {
        final PsiTypeCastExpression cast = myCasts.iterator().next();

        cast.accept(new PsiRecursiveElementVisitor() {
                      public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
                        super.visitTypeCastExpression(expression);

                        if (myCasts.contains(expression)) {
                          if (expression.getType().equals(expression.getOperand().getType())) {
                            try {
                              expression.replace(expression.getOperand());
                              myCastsRemoved++;
                            }
                            catch (IncorrectOperationException e1) {
                              LOG.error(e1);
                            }
                          }

                          myCasts.remove(expression);
                        }
                      }
                    });
      }
    }
  }

  private String getRatio(final int x, final int y) {
    final String ratio = RefactoringBundle.message("type.cook.ratio.generified", x, y);
    return ratio + (y != 0 ? " (" + (x * 100 / y) + "%)" : "");
  }

  public String getReport() {
    return RefactoringBundle.message("type.cook.report", getRatio(myCookedNumber, myVictims.size()),
                                     getRatio(myCastsRemoved, myCastsNumber));
  }
}
