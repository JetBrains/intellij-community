package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor.getLoggerName;

public class UseSlf4jAnnotationQuickFix extends AddAnnotationFix implements IntentionAction {

  @NotNull
  private final SmartPsiElementPointer<PsiNamedElement> elementToRemove;

  public UseSlf4jAnnotationQuickFix(@NotNull PsiNamedElement elementToRemove, @NotNull PsiClass containingClass) {
    super(Slf4j.class.getName(), containingClass);
    this.elementToRemove = SmartPointerManager.getInstance(elementToRemove.getProject()).createSmartPsiElementPointer(elementToRemove);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    super.invoke(project, file, startElement, endElement);

    final PsiNamedElement psiNamedElement = elementToRemove.getElement();
    if (null != psiNamedElement) {
      final Collection<PsiReference> all = ReferencesSearch.search(psiNamedElement).findAll();

      final String loggerName = getLoggerName(PsiTreeUtil.getParentOfType(psiNamedElement, PsiClass.class));
      for (PsiReference psiReference : all) {
        psiReference.handleElementRename(loggerName);
      }

      psiNamedElement.delete();

      JavaCodeStyleManager.getInstance(project).removeRedundantImports((PsiJavaFile) file);
    }
  }
}
