package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.usageView.UsageInfo;

/**
 * @author cdr
 */
public class SliceBackwardHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiExpression expression = getExpressionAtCaret(editor, file);
    if (expression == null) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find what to slice. Please stand on the expression and try again.");
      return;
    }
    slice(expression);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static void slice(final PsiExpression expression) {
    final Project project = expression.getProject();

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation


    final Content[] myContent = new Content[1];
    final ContentManager contentManager = SliceManager.getInstance(project).getContentManager();
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(project);
    SlicePanel slicePanel = new SlicePanel(project, new SliceUsage(new UsageInfo(expression), null)) {
      public void dispose() {
        super.dispose();
        contentManager.removeContent(myContent[0], true);
      }

      public boolean isAutoScroll() {
        return sliceToolwindowSettings.isAutoScroll();
      }

      public void setAutoScroll(boolean autoScroll) {
        sliceToolwindowSettings.setAutoScroll(autoScroll);
      }

      public boolean isPreview() {
        return sliceToolwindowSettings.isPreview();
      }

      public void setPreview(boolean preview) {
        sliceToolwindowSettings.setPreview(preview);
      }
    };
    myContent[0] = contentManager.getFactory().createContent(slicePanel, "slices", true);
    contentManager.addContent(myContent[0]);
    contentManager.setSelectedContent(myContent[0]);

    ToolWindowManager.getInstance(project).getToolWindow("Slice").activate(new Runnable(){
      public void run() {
        //mySlicePanel.sliceFinished();
      }
    });
  }

  private static PsiExpression getExpressionAtCaret(final Editor editor, final PsiFile file) {
    PsiElement element = BaseRefactoringAction.getElementAtCaret(editor, file);
    return PsiTreeUtil.getParentOfType(element, PsiExpression.class);
  }
}
