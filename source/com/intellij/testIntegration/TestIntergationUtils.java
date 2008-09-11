package com.intellij.testIntegration;

import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.ArrayList;

public class TestIntergationUtils {
  public static boolean isTest(PsiElement element) {
    PsiClass klass = findOuterClass(element);
    return klass != null && TestUtil.isTestClass(klass);
  }

  public static PsiClass findOuterClass(PsiElement element) {
    PsiClass result = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (result == null) return null;

    do {
      PsiClass nextParent = PsiTreeUtil.getParentOfType(result, PsiClass.class, true);
      if (nextParent == null) return result;
      result = nextParent;
    }
    while (true);
  }

  public static List<MemberInfo> extractClassMethods(PsiClass clazz, boolean includeInherited) {
    List<MemberInfo> result = new ArrayList<MemberInfo>();

    do {
      MemberInfo.extractClassMembers(clazz, result, new MemberInfo.Filter() {
        public boolean includeMember(PsiMember member) {
          if (!(member instanceof PsiMethod)) return false;
          PsiModifierList list = member.getModifierList();
          return list.hasModifierProperty(PsiModifier.PUBLIC);
        }
      }, false);
      clazz = clazz.getSuperClass();
    }
    while (clazz != null
           && clazz.getSuperClass() != null // not the Object
           && includeInherited);

    return result;
  }

  public static PsiMethod createMethod(PsiClass targetClass, String name, String annotation) throws IncorrectOperationException {
    PsiElementFactory f = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();
    PsiMethod result = f.createMethod(name, PsiType.VOID);
    result.getBody().add(f.createCommentFromText("// Add your code here", result));

    if (annotation != null) {
      PsiAnnotation a = f.createAnnotationFromText("@" + annotation, result);
      PsiModifierList modifiers = result.getModifierList();
      PsiElement first = modifiers.getFirstChild();
      if (first == null) {
        modifiers.add(a);
      }
      else {
        modifiers.addBefore(a, first);
      }

      JavaCodeStyleManager.getInstance(targetClass.getProject()).shortenClassReferences(modifiers);
    }

    return result;
  }

}
