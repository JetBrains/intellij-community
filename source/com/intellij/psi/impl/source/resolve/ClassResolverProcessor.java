package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.processor.PsiResolverProcessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClassResolverProcessor extends BaseScopeProcessor implements NameHint, ElementClassHint, PsiResolverProcessor {
  private final String myClassName;
  private PsiElement myPlace;
  private PsiClass myAccessClass = null;
  private boolean myAccessibleResultsFlag = false;
  private List<CandidateInfo> myCandidates = null;
  private ResolveResult[] myResult = ResolveResult.EMPTY_ARRAY;
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

  public ResolveResult[] getResult() {
    if (myResult != null) return myResult;
    if (myCandidates == null) return myResult = ResolveResult.EMPTY_ARRAY;

    {
      // normalizing
      if (myAccessibleResultsFlag && myCandidates.size() > 1) {
        final ClassCandidateInfo[] infos = myCandidates.toArray(new ClassCandidateInfo[myCandidates.size()]);
        for (int i = 0; i < infos.length; i++) {
          final ClassCandidateInfo info = infos[i];
          if (!info.isAccessible()) {
            myCandidates.remove(info);
          }
        }
      }
    }
    myResult = myCandidates.toArray(new ResolveResult[myCandidates.size()]);
    return myResult;
  }

  public String getName() {
    return myClassName;
  }

  public boolean shouldProcess(Class elementClass) {
    return PsiClass.class.isAssignableFrom(elementClass);
  }

  private boolean myGrouped = false;
  private boolean myStaticContext = false;

  public void handleEvent(Event event, Object associated) {
    if (event == Event.BEGIN_GROUP) {
      myGrouped = true;
    }
    else if (event == Event.END_GROUP) {
      myGrouped = false;
    }
    else if (event == Event.START_STATIC) {
      myStaticContext = true;
    } else if (event == Event.SET_CURRENT_FILE_CONTEXT) {
      myCurrentFileContext = (PsiElement)associated;
    }
    else if (event == Event.SET_DECLARATION_HOLDER) {
    }
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      final String name = aClass.getName();
      if (myClassName.equals(name)) {
        boolean accessible;

        if (myPlace != null) {
          accessible = checkAccessibility(aClass);
        }
        else {
          accessible = true;
        }

        if (accessible) {
          myAccessibleResultsFlag = true;
          if (!myGrouped) {
            myCandidates = null;
            myResult = new ResolveResult[]{new CandidateInfo(aClass, substitutor, null, null, false, myCurrentFileContext)};
            return false;
          }
        }

        myResult = null;
        if (myCandidates == null) {
          myCandidates = new ArrayList<CandidateInfo>();
        }
        else {
          String fqName = aClass.getQualifiedName();
          if (fqName != null) {
            final Iterator iterator = myCandidates.iterator();
            while (iterator.hasNext()) {
              final ClassCandidateInfo info = (ClassCandidateInfo)iterator.next();
              if (fqName.equals(info.getCandidate().getQualifiedName())) {
                return true;
              }
            }
          }
        }
        myCandidates.add(new ClassCandidateInfo(aClass, substitutor, !accessible, myGrouped, myCurrentFileContext));
      }
    }
    return true;
  }

  private boolean checkAccessibility(final PsiClass aClass) {
    //We don't care about accessibility in javadocs
    
    if (ResolveUtil.findParentContextOfClass(myPlace, PsiDocComment.class, false) != null) {
      return true;
    }

    boolean accessible = true;

    if (aClass.getContainingFile() instanceof JspFile) {
      PsiFile file = ResolveUtil.getContextFile(myPlace);
      if (file instanceof JspFile) {
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
          if (myAccessClass != null) {
            accessible = manager.getResolveHelper().isAccessible(aClass, myPlace, myAccessClass);
          }
          else {
            accessible = true;
          }
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

  public void forceResult(ResolveResult[] result) {
    myResult = result;
  }

  public String getProcessorType() {
    return "class resolver";
  }
}
