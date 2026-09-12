package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
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
      String name = annotation.getQualifiedName();
      if (!LombokClassNames.LOMBOK_LOGGERS.contains(name)) return;
      check(annotation, name);
    }

    private void check(@NotNull PsiAnnotation annotation, String name) {
      if (!(annotation.getOwner() instanceof PsiModifierList modifierList) ||
          !(modifierList.getParent() instanceof PsiClass psiClass)) return;

      var logger = psiClass.findFieldByName(AbstractLogProcessor.getLoggerName(psiClass), false);
      if (logger == null || logger.getNavigationElement() != annotation || !logger.hasModifierProperty(PsiModifier.PRIVATE)) return;
      if (ReferencesSearch.search(logger, new LocalSearchScope(psiClass)).findFirst() != null) return;

      var loggerName = StringUtil.getShortName(name);
      holder.problem(annotation, LombokBundle.message("inspection.message.logger.not.used", loggerName))
        .fix(new RemoveAnnotationQuickFix(annotation, psiClass))
        .register();
    }
  }
}
