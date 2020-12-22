package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.ValProcessor;
import de.plushnikov.intellij.plugin.provider.LombokProcessorProvider;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends AbstractBaseJavaLocalInspectionTool {

  private final ValProcessor valProcessor;

  public LombokInspection() {
    valProcessor = ServiceManager.getService(ValProcessor.class);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private class LombokElementVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    public LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);

      valProcessor.verifyVariable(variable, holder);
    }

    @Override
    public void visitParameter(PsiParameter parameter) {
      super.visitParameter(parameter);

      valProcessor.verifyParameter(parameter, holder);
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      final Collection<LombokProblem> problems = new HashSet<>();

      final LombokProcessorProvider processorProvider = LombokProcessorProvider.getInstance(annotation.getProject());
      for (Processor inspector : processorProvider.getProcessors(annotation)) {
        problems.addAll(inspector.verifyAnnotation(annotation));
      }

      for (LombokProblem problem : problems) {
        holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
      }
    }

    /**
     * Check MethodCallExpressions for calls for default (argument less) constructor
     * Produce an error if resolved constructor method is build by lombok and contains some arguments
     */
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
      super.visitMethodCallExpression(methodCall);

      PsiExpressionList list = methodCall.getArgumentList();
      PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();

      boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
      final int parameterCount = list.getExpressions().length;
      if (isThisOrSuper && parameterCount == 0) {

        JavaResolveResult[] results = referenceToMethod.multiResolve(true);
        JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
        PsiElement resolved = resolveResult.getElement();

        if (resolved instanceof LombokLightMethodBuilder && ((LombokLightMethodBuilder) resolved).getParameterList().getParameters().length != 0) {
          holder.registerProblem(methodCall, "Default constructor doesn't exist", ProblemHighlightType.ERROR);
        }
      }
    }
  }
}
