// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.DataProviderReference;
import com.theoryinpractice.testng.TestNGFramework;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.theoryinpractice.testng.util.TestNGUtil.DATA_PROVIDER_ANNOTATION_FQN;
import static com.theoryinpractice.testng.util.TestNGUtil.DATA_PROVIDER_ATTRIBUTE;
import static com.theoryinpractice.testng.util.TestNGUtil.TEST_ANNOTATION_FQN;
import static com.theoryinpractice.testng.util.TestNGUtil.extractDataProviderClass;
import static com.theoryinpractice.testng.util.TestNGUtil.getAttributeValue;

public class MalformedDataProviderInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnnotation(@NotNull PsiAnnotation annotation) {
        if (!TEST_ANNOTATION_FQN.equals(annotation.getQualifiedName())) return;

        final PsiAnnotationMemberValue provider = annotation.findDeclaredAttributeValue(DATA_PROVIDER_ATTRIBUTE);
        if (provider == null || TestNGUtil.isDisabled(annotation)) return;

        PsiReference dataProviderReference = ContainerUtil.find(provider.getReferences(), DataProviderReference.class::isInstance);
        if (dataProviderReference == null) return;
        final PsiElement dataProviderMethod = dataProviderReference.resolve();
        final PsiElement element = dataProviderReference.getElement();
        final PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
        final List<PsiClass> providerClasses = TestNGUtil.getProviderClasses(element, topLevelClass);
        if (dataProviderMethod instanceof PsiMethod providerMethod && !providerClasses.isEmpty()) {
          if (!TestNGUtil.isVersionOrGreaterThan(holder.getProject(), ModuleUtilCore.findModuleForPsiElement(providerClasses.getFirst()), 6, 9,
                                                 13) &&
              !providerClasses.contains(topLevelClass)  &&
              !providerMethod.hasModifierProperty(PsiModifier.STATIC)) {
            holder.registerProblem(provider, TestngBundle.message("inspection.testng.data.provider.need.to.be.static"));
          }
        }
        else {
          PsiMethod testMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          PsiClass containingClass = testMethod != null ? testMethod.getContainingClass() : null;
          if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) return;

          final LocalQuickFix[] fixes = (isOnTheFly && !providerClasses.isEmpty())
                                        ? toArray(createMethodFix(provider, providerClasses.getFirst(), topLevelClass))
                                        : LocalQuickFix.EMPTY_ARRAY;
          holder.registerProblem(provider, TestngBundle.message("inspection.testng.data.provider.does.not.exist.problem"), fixes);
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.isInterface() || aClass.isAnnotationType()) return;
        PsiIdentifier identifier = aClass.getNameIdentifier();
        if (identifier == null) return;
        String name = findMissingDataProviderName(aClass);
        if (name == null) return;
        final LocalQuickFix[] fixes = (isOnTheFly)
                                      ? toArray(createMethodFix(name, aClass, aClass))
                                      : LocalQuickFix.EMPTY_ARRAY;
        holder.registerProblem(identifier, TestngBundle.message("inspection.testng.data.provider.does.not.exist.problem"), fixes);
      }

      /**
       * Identifies the name of a missing TestNG data provider, if applicable, in the specified class.
       *
       * @param aClass the {@link PsiClass} to analyze for identifying any missing data provider references.
       * @return the name of the missing data provider if one is found and is not yet defined in the inspected class;
       *         otherwise, returns null.
       */
      private static @Nullable String findMissingDataProviderName(@NotNull PsiClass aClass) {
        if (hasDataProviderClass(aClass)) return null;

        Set<String> dataProviderNamesCache = new HashSet<>();
        Set<String> candidates = new HashSet<>();

        for (PsiMethod method : aClass.getAllMethods()) {
          // data provider
          PsiAnnotation dpAnnotation = AnnotationUtil.findAnnotation(method, true, DATA_PROVIDER_ANNOTATION_FQN);
          if (dpAnnotation != null) {
            String name = getAttributeValue(dpAnnotation, "name");
            name = (name != null && !name.isEmpty()) ? name : method.getName();
            dataProviderNamesCache.add(name);
            continue;
          }

          PsiClass declaringClass = method.getContainingClass();
          if (declaringClass == null || declaringClass == aClass) continue; // the method already has a warning see: #visitAnnotation

          PsiAnnotation testAnnotation = AnnotationUtil.findAnnotation(method, true, TEST_ANNOTATION_FQN);
          if (testAnnotation == null || TestNGUtil.isDisabled(testAnnotation)) continue;

          String providerName = getAttributeValue(testAnnotation, DATA_PROVIDER_ATTRIBUTE);
          if (providerName == null || providerName.isEmpty()) continue;

          if (
            // doesn't have a data provider class specified in the annotations
            extractDataProviderClass(testAnnotation) == null &&
            // The data provider's method has been found, and the test method should not be tested later.
            !dataProviderNamesCache.contains(providerName)
          ) {
            candidates.add(providerName);
          }
        }

        return ContainerUtil.find(candidates, candidate -> !dataProviderNamesCache.contains(candidate));
      }

      /**
       * Checks if the class hierarchy has a data provider class specified in the annotations.
       * @param aClass the class to check.
       * @return true if the class hierarchy has a data provider class specified in the annotations, false otherwise.
       */
      private static boolean hasDataProviderClass(PsiClass aClass) {
        while (aClass != null) {
          for (PsiAnnotation annotation : aClass.getAnnotations()) {
            PsiAnnotationMemberValue value = extractDataProviderClass(annotation);
            if (value != null) return true;
          }
          aClass = aClass.getSuperClass();
        }
        return false;
      }

      private static LocalQuickFix[] toArray(@Nullable LocalQuickFix fix) {
        return (fix == null) ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{fix};
      }
    };
  }

  private static @Nullable CreateMethodQuickFix createMethodFix(@NotNull PsiAnnotationMemberValue provider,
                                                                @NotNull PsiClass providerClass,
                                                                @Nullable PsiClass topLevelClass) {
    return createMethodFix(StringUtil.unquoteString(provider.getText()), providerClass, topLevelClass);
  }

  private static @Nullable CreateMethodQuickFix createMethodFix(@NotNull String name,
                                                                @NotNull PsiClass providerClass,
                                                                @Nullable PsiClass topLevelClass) {
    FileTemplateDescriptor templateDesc = new TestNGFramework().getParametersMethodFileTemplateDescriptor();
    assert templateDesc != null;
    final FileTemplate fileTemplate = FileTemplateManager.getInstance(providerClass.getProject())
      .getCodeTemplate(templateDesc.getFileName());

    String body = "";
    try {
      final Properties attributes = FileTemplateManager.getInstance(providerClass.getProject()).getDefaultProperties();
      attributes.setProperty(FileTemplate.ATTRIBUTE_NAME, name);
      final PsiCodeBlock methodBody = JavaPsiFacade.getElementFactory(providerClass.getProject())
        .createMethodFromText(fileTemplate.getText(attributes).replace("${BODY}\n", ""), providerClass)
        .getBody();
      if (methodBody != null) {
        body = StringUtil.trimEnd(StringUtil.trimStart(methodBody.getText(), "{"), "}");
      }
    }
    catch (Exception ignored) {
    }

    String signature = "@%s public %sObject[][] %s()"
      .formatted(DATA_PROVIDER_ANNOTATION_FQN, (providerClass == topLevelClass) ? "static " : "", name);
    return CreateMethodQuickFix.createFix(providerClass, signature, StringUtil.isEmptyOrSpaces(body) ? "return new Object[][]{};" : body);
  }
}
