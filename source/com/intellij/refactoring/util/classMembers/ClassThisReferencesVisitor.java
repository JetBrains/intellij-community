package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;

/**
 * Visits explicit and implicit references to 'this'
 * @author dsl
 */
public abstract class ClassThisReferencesVisitor extends ClassMemberReferencesVisitor {
  HashSet myClassSuperClasses;
  HashMap mySupers;
  public ClassThisReferencesVisitor(PsiClass aClass) {
    super(aClass);
    myClassSuperClasses = new HashSet();
    myClassSuperClasses.add(aClass);
    mySupers = new HashMap();
  }

  public void visitThisExpression(PsiThisExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if(ref != null) {
      PsiElement element = ref.resolve();
      if(element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
        if(myClassSuperClasses.contains(aClass)) {
          visitExplicitThis(aClass, expression);
        }
        if(aClass.isInheritor(getPsiClass(), true)) {
          myClassSuperClasses.add(aClass);
          visitExplicitThis(aClass, expression);
        }
      }
      ref.accept(this);
    }
    else {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if(containingClass != null) {
        if(getPsiClass().getManager().areElementsEquivalent(getPsiClass(), containingClass)) {
          visitExplicitThis(getPsiClass(), expression);
        }
      }
    }
  }

  public void visitSuperExpression(PsiSuperExpression expression) {
    PsiJavaCodeReferenceElement ref = expression.getQualifier();
    if (ref != null) {
      PsiElement element = ref.resolve();
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass) element;
        if (myClassSuperClasses.contains(aClass)) {
          visitExplicitSuper(getSuper(aClass), expression);
        }
        if (aClass.isInheritor(getPsiClass(), true)) {
          myClassSuperClasses.add(aClass);
          visitExplicitSuper(getSuper(aClass), expression);
        }
      }
      ref.accept(this);
    }
    else {
      PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (containingClass != null) {
        if (getPsiClass().getManager().areElementsEquivalent(getPsiClass(), containingClass)) {
          visitExplicitSuper(getSuper(getPsiClass()), expression);
        }
      }
    }
  }

  private PsiClass getSuper(final PsiClass aClass) {
    PsiClass result = (PsiClass) mySupers.get(aClass);
    if(result == null) {
      PsiClass[] supers = aClass.getSupers();
      if(supers.length > 0) {
        result = supers[0];
        mySupers.put(aClass, result);
      }
    }
    return result;
  }


  protected abstract void visitExplicitThis(PsiClass referencedClass, PsiThisExpression reference);
  protected abstract void visitExplicitSuper(PsiClass referencedClass, PsiSuperExpression reference);
}
