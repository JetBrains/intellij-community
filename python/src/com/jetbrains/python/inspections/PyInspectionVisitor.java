package com.jetbrains.python.inspections;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Nov 14, 2008
 * A copy of Ruby's visitor helper.
 */
public class PyInspectionVisitor extends PyElementVisitor {
  private final ProblemsHolder myHolder;

  public PyInspectionVisitor(final ProblemsHolder holder) {
    myHolder = holder;
  }

  public ProblemsHolder getHolder() {
    return myHolder;
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message){
    if (element == null || element.getTextLength() == 0){
      return;
    }
    myHolder.registerProblem(element, message);
  }

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       @NotNull final LocalQuickFix quickFix){
      if (element == null || element.getTextLength() == 0){
          return;
      }
      myHolder.registerProblem(element, message, quickFix);
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message,
                                       final ProblemHighlightType type,
                                       final HintAction action) {
    if (element == null || element.getTextLength() == 0){
        return;
    }
    myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(element, message, type,  action, myHolder.isOnTheFly()));
  }

  /**
   * The most full-blown version.
   * @see ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull final PsiElement psiElement,
    @NotNull final String descriptionTemplate,
    final ProblemHighlightType highlightType,
    final HintAction hintAction,
    final LocalQuickFix... fixes)
  {
    myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(psiElement, descriptionTemplate, highlightType, hintAction,
                                                                           myHolder.isOnTheFly(), fixes));
  }
}
