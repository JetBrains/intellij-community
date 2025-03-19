// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PsiTypeVariableFactory {
  private int myCurrent;
  private final List<Set<PsiTypeVariable>> myClusters = new LinkedList<>();
  private final Map<Integer, Set<PsiTypeVariable>> myVarCluster = new HashMap<>();

  public final int getNumber() {
    return myCurrent;
  }

  public final void registerCluster(final Set<PsiTypeVariable> cluster) {
    myClusters.add(cluster);

    for (final PsiTypeVariable aCluster : cluster) {
      myVarCluster.put(Integer.valueOf(aCluster.getIndex()), cluster);
    }
  }

  public final List<Set<PsiTypeVariable>> getClusters() {
    return myClusters;
  }

  public final Set<PsiTypeVariable> getClusterOf(final int var) {
    return myVarCluster.get(Integer.valueOf(var));
  }

  public final PsiTypeVariable create() {
    return create(null);
  }

  public final PsiTypeVariable create(final PsiElement context) {
    return new PsiTypeVariable() {
      private final int myIndex = myCurrent++;
      private final PsiElement myContext = context;

      @Override
      public boolean isValidInContext(final PsiType type) {
        if (myContext == null) {
          return true;
        }

        if (type == null) {
          return true;
        }

        return type.accept(new PsiTypeVisitor<Boolean>() {
          @Override
          public Boolean visitType(final @NotNull PsiType type) {
            return Boolean.TRUE;
          }

          @Override
          public Boolean visitArrayType(final @NotNull PsiArrayType arrayType) {
            return arrayType.getDeepComponentType().accept(this);
          }

          @Override
          public Boolean visitWildcardType(final @NotNull PsiWildcardType wildcardType) {
            final PsiType bound = wildcardType.getBound();

            if (bound != null) {
              bound.accept(this);
            }

            return Boolean.TRUE;
          }

          @Override
          public Boolean visitClassType(final @NotNull PsiClassType classType) {
            final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            final PsiClass aClass = result.getElement();
            final PsiSubstitutor aSubst = result.getSubstitutor();

            if (aClass != null) {
              final PsiManager manager = aClass.getManager();
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

              if (aClass instanceof PsiTypeParameter) {
                final PsiTypeParameterListOwner owner = PsiTreeUtil.getParentOfType(myContext, PsiTypeParameterListOwner.class);

                if (owner != null) {
                  boolean found = false;

                  for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(owner)) {
                    found = manager.areElementsEquivalent(typeParameter, aClass);
                    if (found) break;
                  }

                  if (!found) {
                    return Boolean.FALSE;
                  }
                }
                else {
                  return Boolean.FALSE;
                }
              }
              else if (!facade.getResolveHelper().isAccessible(aClass, myContext, null)) {
                return Boolean.FALSE;
              }

              for (PsiTypeParameter parm : PsiUtil.typeParametersIterable(aClass)) {
                final PsiType type = aSubst.substitute(parm);

                if (type != null) {
                  final Boolean b = type.accept(this);

                  if (!b.booleanValue()) {
                    return Boolean.FALSE;
                  }
                }
              }

              return Boolean.TRUE;
            }
            else {
              return Boolean.FALSE;
            }
          }
        }).booleanValue();
      }

      @Override
      public @NotNull String getPresentableText() {
        return "$" + myIndex;
      }

      @Override
      public @NotNull String getCanonicalText() {
        return getPresentableText();
      }

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public boolean equalsToText(@NotNull String text) {
        return text.equals(getPresentableText());
      }

      @Override
      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @Override
      public PsiType @NotNull [] getSuperTypes() {
        return EMPTY_ARRAY;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PsiTypeVariable psiTypeVariable && myIndex == psiTypeVariable.getIndex();
      }

      @Override
      public int hashCode() {
        return myIndex;
      }

      @Override
      public int getIndex() {
        return myIndex;
      }
    };
  }
}