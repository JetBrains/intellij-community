package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.typeMigration.rules.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Oct 2, 2004
 * Time: 9:24:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class TypeMigrationRules {
  private final LinkedList<TypeConversionRule> myConversionRules = new LinkedList<TypeConversionRule>();

  private final PsiType myRootType;
  private PsiType myMigrationRootType;
  private SearchScope mySearchScope;

  public TypeMigrationRules(final PsiType root) {
    myRootType = root;
    myConversionRules.add(new RootTypeConversionRule());
    myConversionRules.add(new ListArrayConversionRule());
    myConversionRules.add(new BoxingTypeConversionRule());
    myConversionRules.add(new ElementToArrayConversionRule());
    myConversionRules.add(new AtomicConversionRule());
  }

  public void setMigrationRootType(PsiType migrationRootType) {
    myMigrationRootType = migrationRootType;
  }

  public PsiType getRootType() {
    return myRootType;
  }

  public PsiType getMigrationRootType() {
    return myMigrationRootType;
  }

  public void addConversionDescriptor(TypeConversionRule rule) {
    myConversionRules.add(rule);
  }

  @NonNls
  @Nullable
  public TypeConversionDescriptor findConversion(final PsiType from, final PsiType to, PsiMember member, final PsiElement context, final boolean isCovariantPosition,
                                             final TypeMigrationLabeler labeler) {
    final TypeConversionDescriptor conversion = findConversion(from, to, member, context, labeler);
    if (conversion != null) return conversion;

    final int fLevel = from.getArrayDimensions();
    final int tLevel = to.getArrayDimensions();

    if (fLevel == tLevel) {
      final PsiType fElement = from.getDeepComponentType();
      final PsiType tElement = to.getDeepComponentType();

      if (fElement instanceof PsiClassType && tElement instanceof PsiClassType) {
        final PsiClass fClass = ((PsiClassType)fElement).resolve();
        final PsiClass tClass = ((PsiClassType)tElement).resolve();

        if (fClass == tClass) return new TypeConversionDescriptor();

        if (fClass != null && tClass != null && member instanceof PsiMethod) {
          final HashSet<PsiClass> fClasses = new HashSet<PsiClass>();
          InheritanceUtil.getSuperClasses(fClass, fClasses, true);

          final HashSet<PsiClass> tClasses = new HashSet<PsiClass>();
          InheritanceUtil.getSuperClasses(tClass, tClasses, true);

          fClasses.retainAll(tClasses);

          for (PsiClass psiClass : fClasses) {
            if (MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(fClass, psiClass, ((PsiMethod)member).getSignature(PsiSubstitutor.EMPTY), true) != null) return new TypeConversionDescriptor();
          }
        }
      }


      if (isCovariantPosition && TypeConversionUtil.isAssignable(tElement, fElement)) return new TypeConversionDescriptor();
      if (!isCovariantPosition && TypeConversionUtil.isAssignable(fElement, tElement)) return new TypeConversionDescriptor();
    }

    if (isCovariantPosition && TypeConversionUtil.isAssignable(to, from)) return new TypeConversionDescriptor();
    if (!isCovariantPosition && TypeConversionUtil.isAssignable(from, to)) return new TypeConversionDescriptor();
    return null;
  }

  @Nullable
  public TypeConversionDescriptor findConversion(PsiType from, PsiType to, PsiMember member, PsiElement context, TypeMigrationLabeler labeler) {
    for (TypeConversionRule descriptor : myConversionRules) {
      final TypeConversionDescriptor conversion = descriptor.findConversion(from, to, member, context, labeler);
      if (conversion != null) return conversion;
    }
    return null;
  }

  public void setBoundScope(final SearchScope searchScope) {
    mySearchScope = searchScope;
  }

  public SearchScope getSearchScope() {
    return mySearchScope;
  }

  @Nullable
  public Pair<PsiType, PsiType> bindTypeParameters(final PsiType from, final PsiType to, final PsiMethod method, final PsiElement context, final TypeMigrationLabeler labeler) {
    for (TypeConversionRule conversionRule : myConversionRules) {
      final Pair<PsiType, PsiType> typePair = conversionRule.bindTypeParameters(from, to, method, context, labeler);
      if (typePair != null) return typePair;
    }
    return null;
  }
}
