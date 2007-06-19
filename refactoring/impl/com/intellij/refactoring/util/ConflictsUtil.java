/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ConflictsUtil {
  public static PsiMember getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter))
        return (PsiMember)parent;
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
  }

  public static String getDescription(@NotNull PsiElement element, boolean includeParent) {
    if (element instanceof PsiField) {
      int options = PsiFormatUtil.SHOW_NAME;
      if (includeParent) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      return RefactoringBundle.message("field.description", CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatVariable((PsiVariable)element, options, PsiSubstitutor.EMPTY)));
    }

    if (element instanceof PsiMethod) {
      int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
      if (includeParent) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      final PsiMethod method = (PsiMethod) element;
      return method.isConstructor() ?
             RefactoringBundle.message("constructor.description", CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE))) :
             RefactoringBundle.message("method.description", CommonRefactoringUtil.htmlEmphasize( PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE)));
    }

    if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer) element;
      boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
      String s = isStatic ?
                 RefactoringBundle.message("static.initializer.description", getDescription(initializer.getContainingClass(), false)) :
                 RefactoringBundle.message("instance.initializer.description", getDescription(initializer.getContainingClass(), false));;
      return s;
    }

    if (element instanceof PsiParameter) {
      return RefactoringBundle.message("parameter.description", CommonRefactoringUtil.htmlEmphasize(((PsiParameter)element).getName()));
    }

    if (element instanceof PsiLocalVariable) {
      return RefactoringBundle.message("local.variable.description", CommonRefactoringUtil.htmlEmphasize(((PsiVariable)element).getName()));
    }

    if (element instanceof PsiPackage) {
      return RefactoringBundle.message("package.description", CommonRefactoringUtil.htmlEmphasize(((PsiPackage)element).getName()));
    }

    if ((element instanceof PsiClass)) {
      //TODO : local & anonymous
      PsiClass psiClass = (PsiClass) element;
      return RefactoringBundle.message("class.description", CommonRefactoringUtil.htmlEmphasize(UsageViewUtil.getDescriptiveName(psiClass)));
    }

    final String typeString = UsageViewUtil.getType(element);
    final String name = UsageViewUtil.getDescriptiveName(element);
    return typeString + " " + CommonRefactoringUtil.htmlEmphasize(name);
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public static void checkMethodConflicts(PsiClass aClass,
                                          PsiMethod refactoredMethod,
                                          PsiMethod prototype,
                                          final Collection<String> conflicts) {
    if (prototype == null) return;

    PsiMethod method = aClass.findMethodBySignature(prototype, true);

    if (method != null && method != refactoredMethod) {
      if (method.getContainingClass().equals(aClass)) {
        final String classDescr = aClass instanceof PsiAnonymousClass ?
                                  RefactoringBundle.message("current.class") :
                                  getDescription(aClass, false);
        conflicts.add(RefactoringBundle.message("method.0.is.already.defined.in.the.1",
                                                getMethodPrototypeString(prototype),
                                                classDescr));
      }
      else { // method somewhere in base class
        if (method.getManager().getResolveHelper().isAccessible(method, aClass, null)) {
          String protoMethodInfo = getMethodPrototypeString(prototype);
          String className = CommonRefactoringUtil.htmlEmphasize(UsageViewUtil.getDescriptiveName(method.getContainingClass()));
          if (PsiUtil.getAccessLevel(prototype.getModifierList()) >= PsiUtil.getAccessLevel(method.getModifierList()) ) {
            boolean isMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
            boolean isMyMethodAbstract = refactoredMethod != null && refactoredMethod.hasModifierProperty(PsiModifier.ABSTRACT);
            final String conflict = isMethodAbstract != isMyMethodAbstract ?
                                    RefactoringBundle.message("method.0.will.implement.method.of.the.base.class", protoMethodInfo, className) :
                                    RefactoringBundle.message("method.0.will.override.a.method.of.the.base.class", protoMethodInfo, className);
            conflicts.add(conflict);
          }
          else { // prototype is private, will be compile-error
            conflicts.add(RefactoringBundle.message("method.0.will.hide.method.of.the.base.class",
                                                    protoMethodInfo, className));
          }
        }
      }
    }
  }

  private static String getMethodPrototypeString(final PsiMethod prototype) {
    return PsiFormatUtil.formatMethod(
      prototype,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
  }

  public static void checkFieldConflicts(PsiClass aClass, String newName, final Collection<String> conflicts) {
    PsiField existingField = aClass.findFieldByName(newName, true);
    if (existingField != null) {
      if (existingField.getContainingClass().equals(aClass)) {
        String className = aClass instanceof PsiAnonymousClass ?
                           RefactoringBundle.message("current.class") :
                           getDescription(aClass, false);
        final String conflict = RefactoringBundle.message("field.0.is.already.defined.in.the.1",
                                                          existingField.getName(), className);
        conflicts.add(conflict);
      }
      else { // method somewhere in base class
        if (!existingField.hasModifierProperty(PsiModifier.PRIVATE)) {
          String fieldInfo = PsiFormatUtil.formatVariable(existingField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER, PsiSubstitutor.EMPTY);
          String className = getDescription(existingField.getContainingClass(), false);
          final String descr = RefactoringBundle.message("field.0.will.hide.field.1.of.the.base.class",
                                                         newName, fieldInfo, className);
          conflicts.add(descr);
        }
      }
    }
  }
}
