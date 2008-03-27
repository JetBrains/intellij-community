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
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceBackwardHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiParameter parameter = getParameterAtCaret(editor, file);
    if (parameter == null) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find what to slice. Please stand on the method parameter and try again.");
      return;
    }
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    slice(parameter, method);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static void slice(final PsiParameter parameter, final PsiMethod method) {
    final Project project = method.getProject();

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    Map<SliceUsage, List<SliceUsage>> targetEqualUsages =
        new THashMap<SliceUsage, List<SliceUsage>>(new TObjectHashingStrategy<SliceUsage>() {
          public int computeHashCode(SliceUsage object) {
            return object.getUsageInfo().hashCode();
          }

          public boolean equals(SliceUsage o1, SliceUsage o2) {
            return o1.getUsageInfo().equals(o2.getUsageInfo());
          }
        });

    final Content[] myContent = new Content[1];
    final ContentManager contentManager = SliceManager.getInstance(project).getContentManager();
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(project);
    SlicePanel slicePanel = new SlicePanel(project, new SliceUsageRoot(parameter, targetEqualUsages)) {
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

  private static PsiParameter getParameterAtCaret(final Editor editor, final PsiFile file) {
    PsiElement element = BaseRefactoringAction.getElementAtCaret(editor, file);
    PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    return isMethodParameter(parameter) ? parameter : null;
  }

  private static boolean isMethodParameter(PsiElement element) {
    if (!(element instanceof PsiParameter)) return false;
    PsiParameter parameter = (PsiParameter)element;
    return parameter.getDeclarationScope() instanceof PsiMethod;
  }
}
