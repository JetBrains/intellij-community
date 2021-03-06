// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman
 */
public class ConvertOldAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "ConvertOldAnnotations";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitAnnotation(final PsiAnnotation annotation) {
        final String qualifiedName = annotation.getQualifiedName();
        if (Comparing.strEqual(qualifiedName, "org.testng.annotations.Configuration")) {
          holder.registerProblem(annotation, TestngBundle.message("inspection.message.old.testng.annotation.configuration.used"), new ConvertOldAnnotationsQuickfix());
        }
      }
    };
  }

  private static class ConvertOldAnnotationsQuickfix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(ConvertOldAnnotationsQuickfix.class);

    @Override
    @NotNull
    public String getFamilyName() {
      return TestngBundle.message("intention.family.name.convert.old.configuration.testng.annotations");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiAnnotation annotation = (PsiAnnotation)descriptor.getPsiElement();
      if (!TestNGUtil.checkTestNGInClasspath(annotation)) return;
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(annotation)) return;
      WriteAction.run(() -> doFix(annotation));
    }

    private static void doFix(PsiAnnotation annotation) {
      final PsiModifierList modifierList = PsiTreeUtil.getParentOfType(annotation, PsiModifierList.class);
      LOG.assertTrue(modifierList != null);
      try {
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "beforeTest", "@org.testng.annotations.BeforeTest");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "beforeTestClass", "@org.testng.annotations.BeforeTest");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "beforeTestMethod",
                                                  "@org.testng.annotations.BeforeMethod");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "beforeSuite", "@org.testng.annotations.BeforeSuite");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "beforeGroups", "@org.testng.annotations.BeforeGroups");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "afterTest", "@org.testng.annotations.AfterTest");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "afterTestClass", "@org.testng.annotations.AfterTest");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "afterTestMethod", "@org.testng.annotations.AfterMethod");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "afterSuite", "@org.testng.annotations.AfterSuite");
        convertOldAnnotationAttributeToAnnotation(modifierList, annotation, "afterGroups", "@org.testng.annotations.AfterGroups");
        annotation.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static void convertOldAnnotationAttributeToAnnotation(PsiModifierList modifierList,
                                                                PsiAnnotation annotation,
                                                                @NonNls String attribute,
                                                                @NonNls String newAnnotation) throws IncorrectOperationException {

    PsiAnnotationParameterList list = annotation.getParameterList();
    Project project = annotation.getProject();
    for (PsiNameValuePair pair : list.getAttributes()) {
      if (attribute.equals(pair.getName())) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiAnnotation newPsiAnnotation = factory.createAnnotationFromText(newAnnotation + "()", modifierList);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(modifierList.addAfter(newPsiAnnotation, null));
      }
    }
  }

}
