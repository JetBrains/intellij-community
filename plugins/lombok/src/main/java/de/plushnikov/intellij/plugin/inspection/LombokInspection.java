package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteAnnotation;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverrideAnnotation;
import com.intellij.util.JavaPsiConstructorUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.LombokProblemInstance;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends LombokJavaInspectionBase {

  public LombokInspection() {
  }

  @NotNull
  @Override
  protected PsiElementVisitor createVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private static class LombokElementVisitor extends JavaElementVisitor {
    private static final ValProcessor valProcessor = new ValProcessor();
    private final ProblemsHolder holder;

    LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);

      valProcessor.verifyVariable(variable, holder);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);

      valProcessor.verifyParameter(parameter, holder);
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      final Collection<LombokProblem> problems = new HashSet<>();

      for (Processor inspector : LombokProcessorManager.getProcessors(annotation)) {
        problems.addAll(inspector.verifyAnnotation(annotation));
      }

      doAdditionalVerifications(annotation, problems);

      for (LombokProblem problem : problems) {
        holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
      }
    }

    private static void doAdditionalVerifications(@NotNull PsiAnnotation annotation, @NotNull Collection<LombokProblem> problems) {
      verifyBuilderDefault(annotation, problems);
      verifySneakyThrows(annotation, problems);
    }

    private static void verifyBuilderDefault(@NotNull PsiAnnotation annotation, @NotNull Collection<LombokProblem> problems) {
      if (annotation.hasQualifiedName(LombokClassNames.BUILDER_DEFAULT)) {
        final PsiClass parentOfAnnotation = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
        if (null != parentOfAnnotation) {
          if (!parentOfAnnotation.hasAnnotation(LombokClassNames.BUILDER) &&
              !parentOfAnnotation.hasAnnotation(LombokClassNames.SUPER_BUILDER)) {
            final LombokProblemInstance problemInstance = new LombokProblemInstance(
              LombokBundle.message("inspection.message.builder.default.requires.builder.annotation"), ProblemHighlightType.GENERIC_ERROR);
            problemInstance.withLocalQuickFixes(
              () -> PsiQuickFixFactory.createAddAnnotationFix(LombokClassNames.BUILDER, parentOfAnnotation),
              () -> PsiQuickFixFactory.createAddAnnotationFix(LombokClassNames.SUPER_BUILDER, parentOfAnnotation));
            problems.add(problemInstance);
          }
        }
      }
    }

    private static void verifySneakyThrows(@NotNull PsiAnnotation annotation, @NotNull Collection<LombokProblem> problems) {
      if (annotation.hasQualifiedName(LombokClassNames.SNEAKY_THROWS)) {
        final PsiMethod parentOfAnnotation = PsiTreeUtil.getParentOfType(annotation, PsiMethod.class);
        if (null != parentOfAnnotation && parentOfAnnotation.isConstructor()) {
          final PsiMethodCallExpression thisOrSuperCallInConstructor =
            JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(parentOfAnnotation);

          if (null != thisOrSuperCallInConstructor && parentOfAnnotation.getBody().getStatementCount() == 1) {
            final LombokProblemInstance problemInstance = new LombokProblemInstance(
              LombokBundle.message("inspection.message.sneakythrows.calls.to.sibling.super.constructors.excluded"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            problemInstance.withLocalQuickFixes(() -> new RemoveAnnotationQuickFix(annotation, parentOfAnnotation));
            problems.add(problemInstance);
          }
        }
      }
    }

    /**
     * Check MethodCallExpressions for calls for default (argument less) constructor
     * Produce an error if resolved constructor method is build by lombok and contains some arguments
     */
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCall) {
      super.visitMethodCallExpression(methodCall);

      PsiExpressionList list = methodCall.getArgumentList();
      PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();

      boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
      final int parameterCount = list.getExpressions().length;
      if (isThisOrSuper && parameterCount == 0) {

        JavaResolveResult[] results = referenceToMethod.multiResolve(true);
        JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
        PsiElement resolved = resolveResult.getElement();

        if (resolved instanceof LombokLightMethodBuilder &&
            ((LombokLightMethodBuilder)resolved).getParameterList().getParameters().length != 0) {
          holder.registerProblem(methodCall, LombokBundle.message("inspection.message.default.constructor.doesn.t.exist"),
                                 ProblemHighlightType.ERROR);
        }
      }
    }
  }
}
