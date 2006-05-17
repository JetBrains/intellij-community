package com.intellij.openapi.diff.actions;

import com.intellij.ide.DataAccessor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;

abstract class BaseDiffAction extends AnAction {
  protected static final Function<PsiElement, PsiElement> SOURCE_ELEMENT = new Function<PsiElement, PsiElement>() {
    public PsiElement fun(PsiElement psiElement) {
      if (psiElement == null || !psiElement.isValid()) return null;
      PsiElement navigationElement = psiElement.getNavigationElement();
      if (navigationElement != null) psiElement = navigationElement;
      PsiElement parent = psiElement.getParent();
      if (parent instanceof PsiFile) psiElement = parent;
      return psiElement.getNavigationElement();
    }
  };

  protected static final DataAccessor<PsiElement[]> PRIMARY_SOURCES =
    DataAccessor.createArrayConvertor(DataAccessor.PSI_ELEMENT_ARRAY, SOURCE_ELEMENT, PsiElement.class);

  protected static final DataAccessor<PsiElement> PRIMARY_SOURCE =
    DataAccessor.createConvertor(PRIMARY_SOURCES, new Function<PsiElement[], PsiElement>() {
      public PsiElement fun(PsiElement[] psiElements) {
        return psiElements.length == 1 ? psiElements[0] : null;
      }
    });

  public void actionPerformed(AnActionEvent e) {
    DiffRequest diffData;
    try {
      diffData = getDiffData(e.getDataContext());
    }
    catch (DataAccessor.NoDataException e1) {
      diffData = null;
    }
    if (diffData == null) return;
    final DiffContent[] contents = diffData.getContents();
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (int i = 0; i < contents.length; i++) {
          DiffContent content = contents[i];
          if (content.getFile() != null) documentManager.saveDocument(content.getDocument());
        }
      }
    });
    DiffManager.getInstance().getDiffTool().show(diffData);
  }

  public void update(AnActionEvent e) {
    DiffRequest diffData;
    try {
      diffData = getDiffData(e.getDataContext());
    }
    catch (DataAccessor.NoDataException e1) {
      diffData = null;
    }
    boolean enabled;
    if (diffData == null || diffData.getContents() == null) enabled = false;
    else enabled = DiffManager.getInstance().getDiffTool().canShow(diffData);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
    if (enabled) presentation.setVisible(true);
    else disableAction(presentation);
  }

  protected void disableAction(Presentation presentation) {}

  protected abstract DiffRequest getDiffData(DataContext dataContext) throws DataAccessor.NoDataException;

  protected static VirtualFile getDocumentFile(Document document) {
    return FileDocumentManager.getInstance().getFile(document);
  }

  protected static boolean isEditorContent(Document document) {
    VirtualFile editorFile = getDocumentFile(document);
    return editorFile == null || !editorFile.isValid();
  }

  protected static String getDocumentFileUrl(Document document) {
    return getDocumentFile(document).getPresentableUrl();
  }

  protected static String getContentTitle(Document document) {
    VirtualFile editorFile = getDocumentFile(document);
    if (editorFile == null || !editorFile.isValid())
      return DiffBundle.message("diff.content.editor.content.title");
    return editorFile.getPresentableUrl();
  }
}
