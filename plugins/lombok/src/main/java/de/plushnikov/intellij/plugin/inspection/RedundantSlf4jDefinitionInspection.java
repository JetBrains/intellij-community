package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.quickfix.UseSlf4jAnnotationQuickFix;
import org.jetbrains.annotations.NotNull;

public final class RedundantSlf4jDefinitionInspection extends LombokJavaInspectionBase {

  private static final String LOGGER_SLF4J_FQCN = Slf4jProcessor.LOGGER_TYPE;

  @Override
  protected @NotNull PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new LombokDefinitionVisitor(holder);
  }

  private static class LombokDefinitionVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    LombokDefinitionVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      findRedundantDefinition(field);
    }

    private static final CallMatcher LOGGER_FACTORY_GET_LOGGER = CallMatcher.staticCall("org.slf4j.LoggerFactory", "getLogger")
      .parameterTypes("java.lang.Class");

    private static boolean isLoggerInitCall(PsiExpression initializer, PsiClass containingClass) {
      initializer = PsiUtil.skipParenthesizedExprDown(initializer);
      if (!(initializer instanceof PsiMethodCallExpression call) || !LOGGER_FACTORY_GET_LOGGER.test(call)) {
        return false;
      }

      PsiExpression argument = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
      if (!(argument instanceof PsiClassObjectAccessExpression classLiteral)) {
        return false;
      }

      PsiClass referencedClass = PsiUtil.resolveClassInClassTypeOnly(classLiteral.getOperand().getType());
      return referencedClass != null && containingClass.getManager().areElementsEquivalent(referencedClass, containingClass);
    }

    private void findRedundantDefinition(@NotNull PsiField field) {
      if (field.getType().equalsToText(LOGGER_SLF4J_FQCN)) {
        final PsiExpression initializer = field.getInitializer();
        final PsiClass containingClass = field.getContainingClass();
        if (initializer != null && containingClass != null) {
          if (isLoggerInitCall(initializer, containingClass)) {
            holder.registerProblem(field, LombokBundle.message("inspection.message.slf4j.logger.defined.explicitly"),
                                   LocalQuickFix.from(new UseSlf4jAnnotationQuickFix(field, containingClass)));
          }
        }
      }
    }
  }
}
