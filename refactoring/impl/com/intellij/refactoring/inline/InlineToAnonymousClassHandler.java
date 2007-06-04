package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class InlineToAnonymousClassHandler {
  public static void invoke(final Project project, final Editor editor, final PsiClass psiClass) {
    PsiCall callToInline = findCallToInline(editor);

    String errorMessage = getCannotInlineMessage(psiClass);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.to.anonymous.refactoring"), errorMessage, null, project);
      return;
    }

    InlineToAnonymousClassDialog dlg = new InlineToAnonymousClassDialog(project, psiClass, callToInline);
    dlg.show();
  }

  public static PsiCall findCallToInline(final Editor editor) {
    PsiCall callToInline = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor) : null;
    if (reference != null) {
      callToInline = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)reference.getElement());
    }
    return callToInline;
  }

  @Nullable
  public static String getCannotInlineMessage(final PsiClass psiClass) {
    if (psiClass.isAnnotationType()) {
      return "Annotation types cannot be inlined";
    }
    if (psiClass.isInterface()) {
      return "Interfaces cannot be inlined";
    }
    if (psiClass.isEnum()) {
      return "Enums cannot be inlined";
    }
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.message("inline.to.anonymous.no.abstract");
    }

    if (ClassInheritorsSearch.search(psiClass).findFirst() != null) {
      return RefactoringBundle.message("inline.to.anonymous.no.inheritors");
    }

    final PsiClass[] interfaces = psiClass.getInterfaces();
    if (interfaces.length > 1) {
      return RefactoringBundle.message("inline.to.anonymous.no.multiple.interfaces");
    }
    if (interfaces.length == 1) {
      final PsiClass superClass = psiClass.getSuperClass();
      if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        return RefactoringBundle.message("inline.to.anonymous.no.superclass.and.interface");
      }
    }

    final PsiMethod[] methods = psiClass.getMethods();
    for(PsiMethod method: methods) {
      if (method.isConstructor()) {
        PsiReturnStatement stmt = findReturnStatement(method);
        if (stmt != null) {
          return "Class cannot be inlined because its constructor contains 'return' statements";
        }
      }
      else if (method.findSuperMethods().length == 0) {
        if (!ReferencesSearch.search(method).forEach(new CheckAncestorProcessor(psiClass))) {
          return "Class cannot be inlined because it has usages of methods not inherited from its superclass or interface";
        }
      }
    }

    final PsiClass[] innerClasses = psiClass.getInnerClasses();
    for(PsiClass innerClass: innerClasses) {
      PsiModifierList classModifiers = innerClass.getModifierList();
      if (classModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static inner classes";
      }
    }

    final PsiField[] fields = psiClass.getFields();
    for(PsiField field: fields) {
      final PsiModifierList fieldModifiers = field.getModifierList();
      if (fieldModifiers != null && fieldModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        Object initValue = null;
        final PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          initValue = psiClass.getManager().getConstantEvaluationHelper().computeConstantExpression(initializer);
        }
        if (initValue == null) {
          return "Class cannot be inlined because it has static fields with non-constant initializers";
        }
      }
      if (!ReferencesSearch.search(field).forEach(new CheckAncestorProcessor(psiClass))) {
        return "Class cannot be inlined because it has usages of fields not inherited from its superclass";
      }
    }

    final PsiClassInitializer[] initializers = psiClass.getInitializers();
    for(PsiClassInitializer initializer: initializers) {
      final PsiModifierList modifiers = initializer.getModifierList();
      if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static initializers";
      }
    }

    return null;
  }

  private static PsiReturnStatement findReturnStatement(final PsiMethod method) {
    final Ref<PsiReturnStatement> stmt = Ref.create(null);
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReturnStatement(final PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        stmt.set(statement);
      }
    });
    return stmt.get();
  }

  private static class CheckAncestorProcessor implements Processor<PsiReference> {
    private final PsiElement myPsiElement;

    public CheckAncestorProcessor(final PsiElement psiElement) {
      myPsiElement = psiElement;
    }

    public boolean process(final PsiReference psiReference) {
      return PsiTreeUtil.isAncestor(myPsiElement, psiReference.getElement(), false);
    }
  }
}