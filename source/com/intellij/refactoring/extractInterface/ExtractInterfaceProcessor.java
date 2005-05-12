package com.intellij.refactoring.extractInterface;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ExtractInterfaceProcessor extends ExtractSuperBaseProcessor {
  public ExtractInterfaceProcessor(Project project,
               boolean replaceInstanceOf,
               PsiDirectory targetDirectory,
               String newClassName,
               PsiClass aClass,
               MemberInfo[] memberInfos,
               JavaDocPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, targetDirectory, newClassName, aClass, memberInfos, javaDocPolicy);
  }

  protected PsiClass extractSuper(String superClassName) throws IncorrectOperationException {
    return ExtractInterfaceHandler.extractInterface(myTargetDirectory, myClass, superClassName, myMemberInfos, myJavaDocPolicy);
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
      final PsiField field = ((PsiField)member);
      return doMemberInfosContain(field);
    }
    else if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) member;
      return doMemberInfosContain(method);
    }
    return false;
  }
}
