package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor;
import org.jetbrains.annotations.NotNull;

public final class UnusedLoggerInspection extends LombokJavaInspectionBase {
  @Override
  protected @NotNull PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new LombokElementVisitor(holder);
  }

  private static class LombokElementVisitor extends JavaElementVisitor {
    private final ProblemsHolder holder;

    LombokElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      checkFor(annotation, LombokClassNames.JAVA_LOG);
      checkFor(annotation, LombokClassNames.LOG_4_J);
      checkFor(annotation, LombokClassNames.LOG_4_J_2);
      checkFor(annotation, LombokClassNames.COMMONS_LOG);
      checkFor(annotation, LombokClassNames.CUSTOM_LOG);
      checkFor(annotation, LombokClassNames.JBOSS_LOG);
      checkFor(annotation, LombokClassNames.FLOGGER);
      checkFor(annotation, LombokClassNames.SLF_4_J);
      checkFor(annotation, LombokClassNames.XSLF_4_J);
    }

    private void checkFor(@NotNull PsiAnnotation annotation, String name) {
      if (!annotation.hasQualifiedName(name)) return;

      var psiClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class, false);
      if (psiClass == null) return;

      var logger = psiClass.findFieldByName(AbstractLogProcessor.getLoggerName(psiClass), false);
      if (logger == null || logger.getNavigationElement() != annotation || !logger.hasModifierProperty(PsiModifier.PRIVATE)) return;
      if (ReferencesSearch.search(logger, new LocalSearchScope(psiClass)).findFirst() != null) return;

      var loggerName = name.substring(name.lastIndexOf('.') + 1);
      holder.problem(annotation, LombokBundle.message("inspection.message.logger.not.used", loggerName))
        .highlight(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        .fix(new RemoveAnnotationQuickFix(annotation, psiClass))
        .register();
    }
  }
}
