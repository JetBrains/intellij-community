package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.LombokProblemInstance;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Plushnikov Michail
 */
public final class LombokInspection extends LombokJavaInspectionBase {

  public LombokInspection() {
  }

  @Override
  protected @NotNull PsiElementVisitor createVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
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
          if (!PsiAnnotationSearchUtil.isAnnotatedWith(parentOfAnnotation, LombokClassNames.BUILDER) &&
              !PsiAnnotationSearchUtil.isAnnotatedWith(parentOfAnnotation, LombokClassNames.SUPER_BUILDER)) {
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
  }
}
