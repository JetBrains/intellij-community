package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.RefactoringMessageUtil;

import java.util.ArrayList;

public class IntroduceVariableHandler extends IntroduceVariableBase {

  protected IntroduceVariableSettings getSettings(final Project project, Editor editor, PsiExpression expr,
                                                  PsiElement[] occurrences, boolean anyAssignmentLHS,
                                                  boolean declareFinalIfAll, PsiType type,
                                                  TypeSelectorManagerImpl typeSelectorManager,
                                                  IntroduceVariableBase.InputValidator validator) {
    ArrayList highlighters = new ArrayList();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (occurrences.length > 1 ) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, attributes, true, highlighters);
      }
    }

    IntroduceVariableDialog dialog = new IntroduceVariableDialog(
            project, expr, occurrences.length, anyAssignmentLHS, declareFinalIfAll,
            typeSelectorManager,
            validator);
    dialog.show();
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
      }
    } else {
      if (editor != null) {
        for (int i = 0; i < highlighters.size(); i++) {
          RangeHighlighter highlighter = (RangeHighlighter) highlighters.get(i);
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  protected void showErrorMessage(String message, final Project project) {
    RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_VARIABLE, project);
  }

  protected void highlightReplacedOccurences(final Project project, Editor editor, final PsiElement[] replacedOccurences) {
    if (editor == null) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    PsiElement[] exprsToHighlight = replacedOccurences;
    highlightManager.addOccurrenceHighlights(editor, exprsToHighlight, attributes, true, null);
    WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
  }

  protected boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]), project);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }
}