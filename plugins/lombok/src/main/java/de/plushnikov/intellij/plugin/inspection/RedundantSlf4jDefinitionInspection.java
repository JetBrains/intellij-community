package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.quickfix.UseSlf4jAnnotationQuickFix;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

public final class RedundantSlf4jDefinitionInspection extends LombokJavaInspectionBase {

  private static final String LOGGER_SLF4J_FQCN = Slf4jProcessor.LOGGER_TYPE;
  private static final String LOGGER_INITIALIZATION = "LoggerFactory.getLogger(%s.class)";

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

    private void findRedundantDefinition(@NotNull PsiField field) {
      if (field.getType().equalsToText(LOGGER_SLF4J_FQCN)) {
        final PsiExpression initializer = field.getInitializer();
        final PsiClass containingClass = field.getContainingClass();
        if (initializer != null && containingClass != null) {
          if (initializer.getText().contains(format(LOGGER_INITIALIZATION, containingClass.getQualifiedName()))) {
            holder.registerProblem(field, LombokBundle.message("inspection.message.slf4j.logger.defined.explicitly"),
                                   LocalQuickFix.from(new UseSlf4jAnnotationQuickFix(field, containingClass)));
          }
        }
      }
    }
  }
}
