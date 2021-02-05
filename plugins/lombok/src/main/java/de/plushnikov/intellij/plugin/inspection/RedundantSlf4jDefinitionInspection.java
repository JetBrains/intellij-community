package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.quickfix.UseSlf4jAnnotationQuickFix;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

public class RedundantSlf4jDefinitionInspection extends LombokJavaInspectionBase {

  private static final String LOGGER_SLF4J_FQCN = Slf4jProcessor.LOGGER_TYPE;
  private static final String LOGGER_INITIALIZATION = "LoggerFactory.getLogger(%s.class)";

  @NotNull
  @Override
  protected PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new LombokDefinitionVisitor(holder);
  }

  private static class LombokDefinitionVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    LombokDefinitionVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      findRedundantDefinition(field, field.getContainingClass());
    }

    private void findRedundantDefinition(PsiVariable field, PsiClass containingClass) {
      if (field.getType().equalsToText(LOGGER_SLF4J_FQCN)) {
        final PsiExpression initializer = field.getInitializer();
        if (initializer != null && containingClass != null) {
          if (initializer.getText().contains(format(LOGGER_INITIALIZATION, containingClass.getQualifiedName()))) {
            holder.registerProblem(field,
                                   LombokBundle.message("inspection.message.slf4j.logger.defined.explicitly"),
                                   ProblemHighlightType.WARNING,
                                   new UseSlf4jAnnotationQuickFix(field, containingClass));
          }
        }
      }
    }
  }
}
