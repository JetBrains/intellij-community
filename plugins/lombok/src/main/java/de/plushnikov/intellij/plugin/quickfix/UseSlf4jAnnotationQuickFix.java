package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static de.plushnikov.intellij.plugin.processor.clazz.log.AbstractLogProcessor.getLoggerName;

public class UseSlf4jAnnotationQuickFix extends AddAnnotationFix implements IntentionAction {

  @NotNull
  private final PsiNamedElement elementToRemove;
  @NotNull
  private final PsiClass containingClass;

  public UseSlf4jAnnotationQuickFix(@NotNull PsiNamedElement elementToRemove, @NotNull PsiClass containingClass) {
    super(Slf4j.class.getName(), containingClass);
    this.elementToRemove = elementToRemove;
    this.containingClass = containingClass;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    super.invoke(project, file, startElement, endElement);

    final Collection<PsiReference> all = ReferencesSearch.search(elementToRemove).findAll();

    final String loggerName = getLoggerName(containingClass);
    for (PsiReference psiReference : all) {
      psiReference.handleElementRename(loggerName);
    }
    elementToRemove.setName(loggerName);
    elementToRemove.delete();
  }
}
