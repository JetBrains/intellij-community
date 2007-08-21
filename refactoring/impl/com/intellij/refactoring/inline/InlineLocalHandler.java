
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class InlineLocalHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineLocalHandler");

  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.variable.title");

  private InlineLocalHandler() {
  }

  /**
   * should be called in AtomicAction
   */
  public static void invoke(@NotNull final Project project, final Editor editor, final PsiLocalVariable local, PsiReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();
    final PsiCodeBlock containerBlock = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
    LOG.assertTrue(containerBlock != null);
    final PsiExpression defToInline = getDefToInline(local, refExpr, containerBlock);
    if (defToInline == null){
      final String key = refExpr == null ? "variable.has.no.initializer" : "variable.has.no.dominating.definition";
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    final Collection<PsiReference> refs = ReferencesSearch.search(local, GlobalSearchScope.allScope(project), false).findAll();

    if (refs.isEmpty()){
      LOG.assertTrue(refExpr == null);
      String message = RefactoringBundle.message("variable.is.never.used", localName);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    final PsiElement[] refsToInline = DefUseUtil.getRefs(containerBlock, local, defToInline);
    if (refsToInline.length == 0) {
      String message = RefactoringBundle.message("variable.is.never.used.before.modification", localName);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      return;
    }

    EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && ArrayUtil.find(refsToInline, refExpr) < 0) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, refExpr);
      LOG.assertTrue(defs.length > 0);
      highlightManager.addOccurrenceHighlights(editor, defs, attributes, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
      WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      return;
    }

    PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final PsiElement[] defs = DefUseUtil.getDefs(containerBlock, local, ref);
      if (defs.length > 1 && !isSameDefinition(defs[0], defToInline)) {
        highlightManager.addOccurrenceHighlights(editor, defs, writeAttributes, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, attributes, true, null);
        String message =
          RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    if (checkRefsInAugmentedAssignment(refsToInline, project, editor, localName)) {
      return;
    }

    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      // TODO : check if initializer uses fieldNames that possibly will be hidden by other
      // locals with the same names after inlining
      highlightManager.addOccurrenceHighlights(
        editor,
        refsToInline,
        attributes, true, null
      );
      int occurrencesCount = refsToInline.length;
      String occurencesString = RefactoringBundle.message("occurences.string", occurrencesCount);
      final String promptKey = isInliningVariableInitializer(defToInline)
                               ? "inline.local.variable.prompt" : "inline.local.variable.definition.prompt";
      final String question = RefactoringBundle.message(promptKey, localName) + " " + occurencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
      dialog.show();
      if (!dialog.isOK()){
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return;
      }
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        try{
          PsiExpression[] exprs = new PsiExpression[refsToInline.length];
          for(int idx = 0; idx < refsToInline.length; idx++){
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)refsToInline[idx];
            exprs[idx] = InlineUtil.inlineVariable(local, defToInline, refElement);
          }

          if (!isInliningVariableInitializer(defToInline)) {
            defToInline.getParent().delete();
          } else {
            defToInline.delete();
          }

          if (ReferencesSearch.search(local).findFirst() == null) {
            QuickFixFactory.getInstance().createRemoveUnusedVariableFix(local).invoke(project, editor, local.getContainingFile());
          }

          if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
            highlightManager.addOccurrenceHighlights(editor, exprs, attributes, true, null);
            WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
          }

          for (final PsiExpression expr : exprs) {
            InlineUtil.tryToInlineArrayCreationForVarargs(expr);
          }
        }
        catch (IncorrectOperationException e){
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, RefactoringBundle.message("inline.command", localName), null);
  }

  private static boolean checkRefsInAugmentedAssignment(final PsiElement[] refsToInline, final Project project, final Editor editor,
                                                        final String localName) {
    for(PsiElement element: refsToInline) {
      if (element.getParent() instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) element.getParent();
        if (element == assignmentExpression.getLExpression()) {
          EditorColorsManager manager = EditorColorsManager.getInstance();
          final TextAttributes writeAttributes = manager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
          HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{element}, writeAttributes, true, null);

          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", localName));
          CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, project);
          WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isSameDefinition(final PsiElement def, final PsiExpression defToInline) {
    if (def instanceof PsiLocalVariable) return defToInline.equals(((PsiLocalVariable)def).getInitializer());
    final PsiElement parent = def.getParent();
    return parent instanceof PsiAssignmentExpression && defToInline.equals(((PsiAssignmentExpression)parent).getRExpression());
  }

  private static boolean isInliningVariableInitializer(final PsiExpression defToInline) {
    return defToInline.getParent() instanceof PsiVariable;
  }

  @Nullable
  private static PsiExpression getDefToInline(final PsiLocalVariable local,
                                              final PsiReferenceExpression refExpr,
                                              final PsiCodeBlock block) {
    if (refExpr != null) {
      PsiElement def;
      if (PsiUtil.isAccessedForWriting(refExpr)) {
        def = refExpr;
      }
      else {
        final PsiElement[] defs = DefUseUtil.getDefs(block, local, refExpr);
        if (defs.length == 1) {
          def = defs[0];
        }
        else {
          return null;
        }
      }

      if (def instanceof PsiReferenceExpression && def.getParent() instanceof PsiAssignmentExpression) {
        final PsiExpression rExpr = ((PsiAssignmentExpression)def.getParent()).getRExpression();
        if (rExpr != null) return rExpr;
      }
    }
    return local.getInitializer();
  }
}
