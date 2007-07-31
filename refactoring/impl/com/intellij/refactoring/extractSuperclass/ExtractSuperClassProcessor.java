package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ExtractSuperClassProcessor extends ExtractSuperBaseProcessor {

  public ExtractSuperClassProcessor(Project project,
                                    PsiDirectory targetDirectory, String newClassName, PsiClass aClass, MemberInfo[] memberInfos, boolean replaceInstanceOf,
                                    JavaDocPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, targetDirectory, newClassName, aClass, memberInfos, javaDocPolicy);
  }


  protected PsiClass extractSuper(final String superClassName) throws IncorrectOperationException {
    return ExtractSuperClassUtil.extractSuperClass(myProject, myTargetDirectory, superClassName, myClass, myMemberInfos, myJavaDocPolicy);
  }

  protected boolean isSuperInheritor(PsiClass aClass) {
    if (!aClass.isInterface()) {
      return myClass.isInheritor(aClass, true);
    }
    else {
      return doesAnyExtractedInterfaceExtends(aClass);
    }
  }

  protected boolean isInSuper(PsiElement member) {
    if (member instanceof PsiField) {
      final PsiClass containingClass = ((PsiField)member).getContainingClass();
      if (myClass.isInheritor(containingClass, true)) return true;
      final PsiField field = ((PsiField)member);
      return doMemberInfosContain(field);
    }
    else if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) member;
      final PsiClass currentSuperClass = myClass.getSuperClass();
      if (currentSuperClass != null) {
        final PsiMethod methodBySignature = currentSuperClass.findMethodBySignature(method, true);
        if (methodBySignature != null) return true;
      }
      return doMemberInfosContain(method);
    }
    return false;
  }


}
