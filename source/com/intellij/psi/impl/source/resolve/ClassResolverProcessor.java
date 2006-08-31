package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClassResolverProcessor extends BaseScopeProcessor implements NameHint, ElementClassHint {
  private final String myClassName;
  private PsiElement myPlace;
  private PsiClass myAccessClass = null;
  private List<ClassCandidateInfo> myCandidates = null;
  private boolean myHasAccessibleCandidate;
  private boolean myHasInaccessibleCandidate;
  private JavaResolveResult[] myResult = JavaResolveResult.EMPTY_ARRAY;
  private PsiElement myCurrentFileContext;

  public ClassResolverProcessor(String className, PsiElement place) {
    myClassName = className;
    myPlace = place;
    final PsiFile file = myPlace.getContainingFile();
    if (file instanceof PsiCodeFragment) {
      if (((PsiCodeFragment)file).getVisibilityChecker() != null) myPlace = null;
    }
    if (place instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)place;
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof PsiClassType) {
          myAccessClass = ((PsiClassType)type).resolve();
        }
      }
    }
  }

  public JavaResolveResult[] getResult() {
    if (myResult != null) return myResult;
    if (myCandidates == null) return myResult = JavaResolveResult.EMPTY_ARRAY;
    if (myHasAccessibleCandidate && myHasInaccessibleCandidate) {
      for (Iterator<ClassCandidateInfo> iterator = myCandidates.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        if (!info.isAccessible()) iterator.remove();
      }
      myHasInaccessibleCandidate = false;
    }

    myResult = myCandidates.toArray(new JavaResolveResult[myCandidates.size()]);
    return myResult;
  }

  public String getName() {
    return myClassName;
  }

  public boolean shouldProcess(Class elementClass) {
    return PsiClass.class.isAssignableFrom(elementClass);
  }

  private boolean myStaticContext = false;

  public void handleEvent(Event event, Object associated) {
    if (event == Event.START_STATIC) {
      myStaticContext = true;
    } else if (event == Event.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      final String name = aClass.getName();
      if (myClassName.equals(name)) {
        if (myCandidates == null) {
          myCandidates = new ArrayList<ClassCandidateInfo>();
        }
        else {
          String fqName = aClass.getQualifiedName();
          if (fqName != null) {
            for (ClassCandidateInfo info : myCandidates) {
              final PsiClass otherClass = info.getElement();
              assert otherClass != null;
              if (fqName.equals(otherClass.getQualifiedName())) {
                return true;
              }
              final PsiClass containingclass1 = aClass.getContainingClass();
              final PsiClass containingclass2 = otherClass.getContainingClass();
              if (containingclass1 != null && containingclass2 != null && containingclass2.isInheritor(containingclass1, true)) {
                //shadowing
                return true;
              }
            }
          }
        }

        boolean accessible = myPlace == null || checkAccessibility(aClass);
        myHasAccessibleCandidate |= accessible;
        myHasInaccessibleCandidate |= !accessible;
        myCandidates.add(new ClassCandidateInfo(aClass, substitutor, !accessible, myCurrentFileContext));
        myResult = null;
        return !accessible;
      }
    }
    return true;
  }

  private boolean checkAccessibility(final PsiClass aClass) {
    //We don't care about accessibility in javadocs

    if (ResolveUtil.isInJavaDoc(myPlace)) {
      return true;
    }

    boolean accessible = true;

    if (PsiUtil.isInJspFile(aClass.getContainingFile())) {
      PsiFile file = ResolveUtil.getContextFile(myPlace);
      if (PsiUtil.isInJspFile(file)) {
        return true;
      }
    }

    if (aClass instanceof PsiTypeParameter) {
      accessible = !(myStaticContext);
    }

    PsiManager manager = aClass.getManager();
    if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiElement parent = aClass.getParent();
      while (true) {
        PsiElement parentScope = parent.getParent();
        if (parentScope instanceof PsiJavaFile) break;
        parent = parentScope;
        if (!(parentScope instanceof PsiClass)) break;
      }
      if (parent instanceof PsiDeclarationStatement) {
        parent = parent.getParent();
      }
      accessible = false;
      for (PsiElement placeParent = myPlace; placeParent != null; placeParent = placeParent.getContext()) {
        if (manager.areElementsEquivalent(placeParent, parent)) accessible = true;
      }
    }
    if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      accessible = false;
      if (manager.arePackagesTheSame(aClass, myPlace)) {
        accessible = true;
      }
      else {
        if (aClass.getContainingClass() != null) {
          accessible = myAccessClass == null || manager.getResolveHelper().isAccessible(aClass, myPlace, myAccessClass);
        }
      }
    }
    if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      if (!manager.arePackagesTheSame(aClass, myPlace)) {
        accessible = false;
      }
    }
    return accessible;
  }

  public void forceResult(JavaResolveResult[] result) {
    myResult = result;
  }

}
