package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class LambdaToAnonymousConverter {
  private final MyInspection myInspection = new MyInspection();

  public void replace(@NotNull Project project, @NotNull PsiLambdaExpression lambda) {
    final InspectionGadgetsFix fix = myInspection.getFix();
    if (fix == null) return;
    try{
      fix.applyFix(project, new MyProblemDescriptorStub(lambda));
    }
    catch (Throwable ignored) {
    }
  }

  private static class MyProblemDescriptorStub extends ProblemDescriptorBase {
    public MyProblemDescriptorStub(@NotNull PsiLambdaExpression lambdaExpression) {
      super(lambdaExpression.getFirstChild(), lambdaExpression.getFirstChild(),
            "", null, ProblemHighlightType.INFORMATION,
            false, null, false, false);
    }

    @Override
    protected void assertPhysical(PsiElement element) {
    }
  }

  private static class MyInspection extends LambdaCanBeReplacedWithAnonymousInspection {
    @Nullable
    InspectionGadgetsFix getFix() {
      return buildFix();
    }
  }
}
