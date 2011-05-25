package com.jetbrains.python.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dcheryasov
 */
public abstract class PyInspectionVisitor extends PyElementVisitor {
  @Nullable private final ProblemsHolder myHolder;
  protected final TypeEvalContext myTypeEvalContext;

  public static final Key<TypeEvalContext> INSPECTION_TYPE_EVAL_CONTEXT = Key.create("PyInspectionTypeEvalContext");

  public PyInspectionVisitor(@Nullable final ProblemsHolder holder) {
    myHolder = holder;
    myTypeEvalContext = TypeEvalContext.fastStubOnly();
  }

  public PyInspectionVisitor(@Nullable ProblemsHolder problemsHolder,
                             @NotNull LocalInspectionToolSession session) {
    myHolder = problemsHolder;
    TypeEvalContext context;
    synchronized (INSPECTION_TYPE_EVAL_CONTEXT) {
      context = session.getUserData(INSPECTION_TYPE_EVAL_CONTEXT);
      if (context == null) {
        context = TypeEvalContext.fastStubOnly();
        session.putUserData(INSPECTION_TYPE_EVAL_CONTEXT, context);
      }
    }
    myTypeEvalContext = context;
  }

  @Nullable protected ProblemsHolder getHolder() {
    return myHolder;
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message){
    if (element == null || element.getTextLength() == 0){
      return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(element, message);
    }
  }

  protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       @NotNull final LocalQuickFix quickFix){
      if (element == null || element.getTextLength() == 0){
          return;
      }
    if (myHolder != null) {
      myHolder.registerProblem(element, message, quickFix);
    }
  }

  protected final void registerProblem(final PsiElement element,
                                       final String message,
                                       final ProblemHighlightType type,
                                       @Nullable final HintAction action) {
    if (element == null || element.getTextLength() == 0){
        return;
    }
    if (myHolder != null) {
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(element, message, type,  action, myHolder.isOnTheFly()));
    }
  }

  /**
   * The most full-blown version.
   * @see com.intellij.codeInspection.ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull final PsiElement psiElement,
    @NotNull final String descriptionTemplate,
    final ProblemHighlightType highlightType,
    @Nullable final HintAction hintAction,
    final LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, highlightType, hintAction, null, fixes);
  }

  /**
   * The most full-blown version.
   * @see ProblemDescriptor
   */
  protected final void registerProblem(
    @NotNull final PsiElement psiElement,
    @NotNull final String descriptionTemplate,
    final ProblemHighlightType highlightType,
    @Nullable final HintAction hintAction,
    @Nullable final TextRange rangeInElement,
    final LocalQuickFix... fixes)
  {
    if (myHolder != null) {
      myHolder.registerProblem(new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false,
                                                         rangeInElement, hintAction, myHolder.isOnTheFly()));
    }
  }
}
