package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.CopyHandler;

public class CopyElementAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }}, "", null
    );

    PsiElement[] elements;
    PsiDirectory defaultTargetDirectory;
    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      Editor editor = DataKeys.EDITOR.getData(dataContext);
      PsiClass aClass = getTopLevelClass(editor, project);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      elements = new PsiElement[]{aClass};
      if (aClass == null || !CopyHandler.canCopy(elements)) {
        elements = new PsiElement[]{file};
      }
      defaultTargetDirectory = file.getContainingDirectory();
    }
    else {
      Object element = dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
      defaultTargetDirectory = element instanceof PsiDirectory ? (PsiDirectory)element : null;
      elements = (PsiElement[])dataContext.getData(DataConstants.PSI_ELEMENT_ARRAY);
    }

    doCopy(elements, defaultTargetDirectory);
  }

  protected void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    CopyHandler.doCopy(elements, null, defaultTargetDirectory);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
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
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    Project project = DataKeys.PROJECT.getData(dataContext);
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
    PsiElement[] elements = (PsiElement[])dataContext.getData(DataConstants.PSI_ELEMENT_ARRAY);
    presentation.setEnabled(elements != null && CopyHandler.canCopy(elements));
    presentation.setVisible(true);
  }

  private PsiClass getTopLevelClass(final Editor editor, final Project project) {
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
