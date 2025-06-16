// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.siyeh.ig.psiutils.CommentTracker;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class LombokGetterOrSetterMayBeUsedInspection extends LombokJavaInspectionBase {

  @Override
  protected @NotNull PsiElementVisitor createVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokGetterOrSetterMayBeUsedVisitor(holder, null);
  }

  private class LombokGetterOrSetterMayBeUsedVisitor extends JavaElementVisitor {
    private final @Nullable ProblemsHolder myHolder;

    private final @Nullable LombokGetterOrSetterMayBeUsedFix myLombokGetterOrSetterMayBeUsedFix;

    private LombokGetterOrSetterMayBeUsedVisitor(@Nullable ProblemsHolder holder,
                                                 @Nullable LombokGetterOrSetterMayBeUsedFix lombokGetterOrSetterMayBeUsedFix) {
      this.myHolder = holder;
      this.myLombokGetterOrSetterMayBeUsedFix = lombokGetterOrSetterMayBeUsedFix;
    }

    @Override
    public void visitJavaFile(@NotNull PsiJavaFile psiJavaFile) {
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      if (psiClass.isRecord()) return;

      List<Pair<PsiField, PsiMethod>> instanceCandidates = new ArrayList<>();
      List<Pair<PsiField, PsiMethod>> staticCandidates = new ArrayList<>();
      for (PsiMethod method : psiClass.getMethods()) {
        processMethod(method, instanceCandidates, staticCandidates);
      }

      if (!instanceCandidates.isEmpty()) {
        final PsiField[] classFields = psiClass.getFields();

        int staticFieldsCount = 0;
        int alreadyAnnotatedFieldsCount = 0;
        for (PsiField classField : classFields) {
          if (classField.hasModifierProperty(PsiModifier.STATIC)) {
            staticFieldsCount++;
          }
          else if (classField.hasAnnotation(getAnnotationName())) {
            alreadyAnnotatedFieldsCount++;
          }
        }

        if (alreadyAnnotatedFieldsCount + instanceCandidates.size() + staticFieldsCount == classFields.length) {
          warnOrFix(psiClass, instanceCandidates);
        }
        else {
          for (Pair<PsiField, PsiMethod> candidate : instanceCandidates) {
            warnOrFix(candidate.getFirst(), candidate.getSecond());
          }
        }
      }

      for (Pair<PsiField, PsiMethod> candidate : staticCandidates) {
        warnOrFix(candidate.getFirst(), candidate.getSecond());
      }
    }

    public void visitMethodForFix(@NotNull PsiMethod psiMethod) {
      List<Pair<PsiField, PsiMethod>> fieldsAndMethods = new ArrayList<>();
      if (!processMethod(psiMethod, fieldsAndMethods, fieldsAndMethods)) return;
      if (!fieldsAndMethods.isEmpty()) {
        final Pair<PsiField, PsiMethod> psiFieldPsiMethodPair = fieldsAndMethods.get(0);
        warnOrFix(psiFieldPsiMethodPair.getFirst(), psiFieldPsiMethodPair.getSecond());
      }
    }

    private void warnOrFix(@NotNull PsiClass psiClass, @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods) {
      if (myHolder != null) {
        String className = psiClass.getName();
        if (StringUtil.isNotEmpty(className)) {
          final PsiIdentifier psiClassNameIdentifier = psiClass.getNameIdentifier();
          final LocalQuickFix fix = new LombokGetterOrSetterMayBeUsedFix(className);
          myHolder.registerProblem(psiClass, getClassErrorMessage(className), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   psiClassNameIdentifier != null ? psiClassNameIdentifier.getTextRangeInParent() : psiClass.getTextRange(),
                                   fix);
        }
      }
      else if (myLombokGetterOrSetterMayBeUsedFix != null) {
        myLombokGetterOrSetterMayBeUsedFix.effectivelyDoFix(psiClass, fieldsAndMethods);
      }
    }

    private void warnOrFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      if (myHolder != null) {
        String fieldName = field.getName();
        final LocalQuickFix fix = new LombokGetterOrSetterMayBeUsedFix(fieldName);
        myHolder.registerProblem(method, getFieldErrorMessage(fieldName), fix);
      }
      else if (myLombokGetterOrSetterMayBeUsedFix != null) {
        myLombokGetterOrSetterMayBeUsedFix.effectivelyDoFix(field, method);
      }
    }
  }

  private class LombokGetterOrSetterMayBeUsedFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myText;

    private LombokGetterOrSetterMayBeUsedFix(@NotNull String text) {
      myText = text;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return getFixName(myText);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return getFixFamilyName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiMethod) {
        new LombokGetterOrSetterMayBeUsedVisitor(null, this).visitMethodForFix((PsiMethod)element);
      }
      else if (element instanceof PsiClass) {
        new LombokGetterOrSetterMayBeUsedVisitor(null, this).visitClass((PsiClass)element);
      }
    }

    private void effectivelyDoFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      // skip adding @Getter/@Setter on field, if @Data or @Value already defined on class and field is not static field
      final PsiClass containingClass = field.getContainingClass();
      if (null == containingClass || field.hasModifierProperty(PsiModifier.STATIC) ||
          (!containingClass.hasAnnotation(LombokClassNames.DATA) && !containingClass.hasAnnotation(LombokClassNames.VALUE) &&
           !containingClass.hasAnnotation(getAnnotationName()) )) {

        field.normalizeDeclaration();

        if (!addLombokAnnotation(field)) return;
      }

      removeMethodAndMoveJavaDoc(field, method);
    }

    public void effectivelyDoFix(@NotNull PsiClass aClass, @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods) {
      // skip adding @Getter/@Setter on class if @Data or @Value already defined
      if (!aClass.hasAnnotation(LombokClassNames.DATA) &&
          !aClass.hasAnnotation(LombokClassNames.VALUE) &&
          !aClass.hasAnnotation(getAnnotationName())) {
        if (!addLombokAnnotation(aClass)) return;
      }

      for (Pair<PsiField, PsiMethod> fieldAndMethod : fieldsAndMethods) {
        PsiField field = fieldAndMethod.getFirst();
        PsiMethod method = fieldAndMethod.getSecond();
        removeMethodAndMoveJavaDoc(field, method);
      }

      // remove existing field annotations if possible
      for (PsiField psiField : aClass.getFields()) {
        if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
          PsiAnnotation oldAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getAnnotationName());
          if (null != oldAnnotation && oldAnnotation.getAttributes().isEmpty()) {
            new CommentTracker().deleteAndRestoreComments(oldAnnotation);
          }
        }
      }
    }

    private boolean addLombokAnnotation(@NotNull PsiModifierListOwner fieldOrClass) {
      final PsiModifierList modifierList = fieldOrClass.getModifierList();
      if (modifierList == null) {
        return false;
      }
      Project project = fieldOrClass.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation annotation = factory.createAnnotationFromText("@" + getAnnotationName(), fieldOrClass);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(annotation);
      modifierList.addAfter(annotation, null);
      return true;
    }

    private void removeMethodAndMoveJavaDoc(@NotNull PsiField field, @NotNull PsiMethod method) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
      CommentTracker tracker = new CommentTracker();
      PsiDocComment methodJavaDoc = method.getDocComment();
      if (methodJavaDoc != null) {
        tracker.text(methodJavaDoc);
        PsiDocComment fieldJavaDoc = field.getDocComment();
        List<String> methodJavaDocTokens =
          Arrays.stream(methodJavaDoc.getChildren()).filter(e -> e instanceof PsiDocToken).map(PsiElement::getText)
            .filter(text -> !text.matches("\\s*\\*\\s*")).toList();
        methodJavaDocTokens = methodJavaDocTokens.subList(1, methodJavaDocTokens.size() - 1);
        String javaDocMethodText = String.join("\n* ", methodJavaDocTokens);
        PsiDocTag[] methodTags = methodJavaDoc.findTagsByName(getTagName());
        if (fieldJavaDoc == null) {
          if (javaDocMethodText.isEmpty()) {
            fieldJavaDoc = factory.createDocCommentFromText("/**\n*/");
          }
          else {
            fieldJavaDoc =
              factory.createDocCommentFromText("/**\n* -- " + getJavaDocMethodMarkup() + " --\n* " + javaDocMethodText + "\n*/");
          }
          for (PsiDocTag methodTag : methodTags) {
            fieldJavaDoc.add(methodTag);
          }
          field.getParent().addBefore(fieldJavaDoc, field);
        }
        else {
          @NotNull PsiElement @NotNull [] fieldJavaDocChildren =
            Arrays.stream(fieldJavaDoc.getChildren()).filter(e -> e instanceof PsiDocToken).toArray(PsiElement[]::new);
          @NotNull PsiElement fieldJavaDocChild = fieldJavaDocChildren[fieldJavaDocChildren.length - 2];
          PsiDocComment newMethodJavaDoc =
            factory.createDocCommentFromText("/**\n* -- " + getJavaDocMethodMarkup() + " --\n* " + javaDocMethodText + "\n*/");
          PsiElement[] tokens = newMethodJavaDoc.getChildren();
          for (int i = tokens.length - 2; 0 < i; i--) {
            fieldJavaDoc.addAfter(tokens[i], fieldJavaDocChild);
          }
          for (PsiDocTag methodTag : methodTags) {
            fieldJavaDoc.add(methodTag);
          }
        }
        methodJavaDoc.delete();
      }
      tracker.delete(method);
      tracker.insertCommentsBefore(field);
    }
  }

  protected abstract @NotNull String getTagName();

  protected abstract @NotNull String getJavaDocMethodMarkup();

  protected abstract @NotNull @NonNls String getAnnotationName();

  protected abstract @NotNull @Nls String getFieldErrorMessage(String fieldName);

  protected abstract @NotNull @Nls String getClassErrorMessage(String className);

  protected abstract boolean processMethod(@NotNull PsiMethod method,
                                           @NotNull List<Pair<PsiField, PsiMethod>> instanceCandidates,
                                           @NotNull List<Pair<PsiField, PsiMethod>> staticCandidates);

  protected abstract @NotNull @Nls String getFixName(String text);

  protected abstract @NotNull @Nls String getFixFamilyName();
}
