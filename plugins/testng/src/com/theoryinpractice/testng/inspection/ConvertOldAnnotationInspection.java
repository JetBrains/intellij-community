/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: 11/09/2006
 * Time: 19:50:29
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:17:59 PM
 */
public class ConvertOldAnnotationInspection extends LocalInspectionTool {
  private static final String DISPLAY_NAME = "Convert old @Configuration TestNG annotations";

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "ConvertOldAnnotations";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
      }

      public void visitAnnotation(final PsiAnnotation annotation) {
        final String qualifiedName = annotation.getQualifiedName();
        if (Comparing.strEqual(qualifiedName, "org.testng.annotations.Configuration")) {
          holder.registerProblem(annotation, DISPLAY_NAME, new ConvertOldAnnotationsQuickfix());
        }
      }
    };
  }

  private static class ConvertOldAnnotationsQuickfix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance("#" + ConvertOldAnnotationsQuickfix.class.getName());

    @NotNull
    public String getName() {
      return DISPLAY_NAME;
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiAnnotation annotation = (PsiAnnotation)descriptor.getPsiElement();
      if (!TestNGUtil.checkTestNGInClasspath(annotation)) return;
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
    for (PsiNameValuePair pair : list.getAttributes()) {
      if (attribute.equals(pair.getName())) {
        final StringBuffer newAnnotationBuffer = new StringBuffer();
        newAnnotationBuffer.append(newAnnotation).append('(').append(')');
        final PsiElementFactory factory = annotation.getManager().getElementFactory();
        final PsiAnnotation newPsiAnnotation = factory.createAnnotationFromText(newAnnotationBuffer.toString(), modifierList);
        CodeStyleManager.getInstance(annotation.getProject()).shortenClassReferences(modifierList.addAfter(newPsiAnnotation, null));
      }
    }
  }

}