package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.CopyHandler;
import org.jetbrains.annotations.Nullable;

public class CopyElementAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }}, "", null
    );
    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    PsiElement[] elements;

    PsiDirectory defaultTargetDirectory = null;
    if (editor != null) {
      PsiClass aClass = getTopLevelClass(editor, project);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      elements = new PsiElement[]{aClass};
      if (aClass == null || !CopyHandler.canCopy(elements)) {
        elements = new PsiElement[]{file};
      }
      assert file != null;
      defaultTargetDirectory = file.getContainingDirectory();
    } else {
      Object element = dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
      defaultTargetDirectory = element instanceof PsiDirectory ? (PsiDirectory)element : null;
      elements = DataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    }
    doCopy(elements, defaultTargetDirectory);
  }

  protected void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    CopyHandler.doCopy(elements, null, defaultTargetDirectory);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(false);
    if (project == null) {
      return;
    }

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      updateForEditor(dataContext, presentation);
    }
    else {
      String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
      updateForToolWindow(id, dataContext, presentation);
    }
  }

  protected void updateForEditor(DataContext dataContext, Presentation presentation) {
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setVisible(false);
      return;
    }

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    PsiClass topLevelClass = getTopLevelClass(editor, project);
    boolean result = topLevelClass != null && CopyHandler.canCopy(new PsiElement[]{topLevelClass});

    if (!result) {
      result = CopyHandler.canCopy(new PsiElement[]{file});
    }

    presentation.setEnabled(result);
    presentation.setVisible(true);
  }

  protected void updateForToolWindow(String toolWindowId, DataContext dataContext,Presentation presentation) {
    PsiElement[] elements = DataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    presentation.setEnabled(elements != null && CopyHandler.canCopy(elements));
  }

  @Nullable
  private static PsiClass getTopLevelClass(final Editor editor, final Project project) {
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    PsiElement element = file.findElementAt(offset);
    if (element == null) element = file;

    while (true) {
      if (element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) break;
      element = element.getParent();
    }
    if (element instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }
    return element instanceof PsiClass ? (PsiClass)element : null;
  }
}
