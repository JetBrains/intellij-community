package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 12, 2005
 * Time: 9:41:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiTypeVariableFactory {
  private int myCurrent = 0;
  private LinkedList<HashSet<PsiTypeVariable>> myClusters = new LinkedList<HashSet<PsiTypeVariable>>();
  private HashMap<Integer, HashSet<PsiTypeVariable>> myVarCluster = new HashMap<Integer, HashSet<PsiTypeVariable>>();

  public final int getNumber() {
    return myCurrent;
  }

  public final void registerCluster(final HashSet<PsiTypeVariable> cluster) {
    myClusters.add(cluster);

    for (final Iterator<PsiTypeVariable> v = cluster.iterator(); v.hasNext();) {
      myVarCluster.put(new Integer(v.next().getIndex()), cluster);
    }
  }

  public final LinkedList<HashSet<PsiTypeVariable>> getClusters() {
    return myClusters;
  }

  public final HashSet<PsiTypeVariable> getClusterOf(final int var) {
    return myVarCluster.get(new Integer(var));
  }

  public final PsiTypeVariable create() {
    return create(null);
  }

  public final PsiTypeVariable create(final PsiElement context) {
    return new PsiTypeVariable() {
      private int myIndex = myCurrent++;
      private final PsiElement myContext = context;

      public boolean isValidInContext(final PsiType type) {
        if (myContext == null) {
          return true;
        }

        if (type == null) {
          return true;
        }

        return type.accept(new PsiTypeVisitor<Boolean>() {
                             public Boolean visitType(final PsiType type) {
                               return Boolean.TRUE;
                             }

                             public Boolean visitArrayType(final PsiArrayType arrayType) {
                               return arrayType.getDeepComponentType().accept(this);
                             }

                             public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
                               final PsiType bound = wildcardType.getBound();

                               if (bound != null) {
                                 bound.accept(this);
                               }

                               return Boolean.TRUE;
                             }

                             public Boolean visitClassType(final PsiClassType classType) {
                               final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
                               final PsiClass aClass = result.getElement();
                               final PsiSubstitutor aSubst = result.getSubstitutor();

                               if (aClass != null) {
                                 final PsiManager manager = aClass.getManager();
                                 final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());

                                 if (aClass instanceof PsiTypeParameter) {
                                   final PsiTypeParameterListOwner owner =
                                   PsiTreeUtil.getParentOfType(myContext, PsiTypeParameterListOwner.class);

                                   if (owner != null) {
                                     boolean found = false;

                                     for (final Iterator<PsiTypeParameter> p = PsiUtil.typeParametersIterator(owner);
                                          p.hasNext() && !found;) {
                                       final PsiTypeParameter parm = p.next();

                                       found = manager.areElementsEquivalent(parm, aClass);
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

                                 final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
                                 while (iterator.hasNext()) {
                                   final PsiTypeParameter parm = iterator.next();
                                   final PsiType type = aSubst.substitute(parm);

                                   if (type != null){
                                     final Boolean b = type.accept(this);

                                     if (! b.booleanValue()){
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

      public String getPresentableText() {
        return "$" + myIndex;
      }

      public String getCanonicalText() {
        return getPresentableText();
      }

      public String getInternalCanonicalText() {
        return getCanonicalText();
      }

      public boolean isValid() {
        return true;
      }

      public boolean equalsToText(String text) {
        return text.equals(getPresentableText());
      }

      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @NotNull
      public PsiType[] getSuperTypes() {
        return new PsiType[0];
      }

      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PsiTypeVariable)) return false;

        final PsiTypeVariable psiTypeVariable = (PsiTypeVariable)o;

        if (myIndex != psiTypeVariable.getIndex()) return false;

        return true;
      }

      public int hashCode() {
        return myIndex;
      }

      public int getIndex() {
        return myIndex;
      }
    };
  }
}
