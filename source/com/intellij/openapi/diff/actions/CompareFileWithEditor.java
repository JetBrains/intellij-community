package com.intellij.openapi.diff.actions;

import com.intellij.ide.macro.DataAccessor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffContentUtil;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.ex.DiffContentFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.ElementPresentation;

public class CompareFileWithEditor extends BaseDiffAction {
  private static final DataAccessor<Document> EDITING_DOCUMENT = new DataAccessor<Document>() {
    public Document getImpl(DataContext dataContext) throws NoDataException {
      VirtualFile[] selectedFiles = FILE_EDITOR_MANAGER.getNotNull(dataContext).getSelectedFiles();
      if (selectedFiles.length == 0) return null;
      if (!DiffContentUtil.isTextFile(selectedFiles[0])) return null;
      return FileDocumentManager.getInstance().getDocument(selectedFiles[0]);
    }
  };

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    FileEditorContents diffData;
    try {
      diffData = getDiffData(e.getDataContext());
    }
    catch (DataAccessor.NoDataException e1) {
      presentation.setText("Compare with Editor");
      presentation.setEnabled(false);
      return;
    }
    String singular = diffData.getElementPresentation().getKind().getSingular(true);
    presentation.setText("Compare " + singular + " with Editor");
    presentation.setEnabled(true);
  }

  protected FileEditorContents getDiffData(DataContext dataContext) throws DataAccessor.NoDataException {
    if (!checkSelection(dataContext)) throw new DataAccessor.NoDataException("Wrong selection");
    Project project = DataAccessor.PROJECT.getNotNull(dataContext);
    PsiElement element = PRIMARY_SOURCE.getNotNull(dataContext);
    PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) element = psiFile;
    Document document = EDITING_DOCUMENT.getNotNull(dataContext);
    if (isSameFile(document, element)) throw new DataAccessor.NoDataException("Same file");
    DiffContent diffContent = DiffContentFactory.fromPsiElement(element);
    if (diffContent == null || diffContent.getDocument() == null)
      throw new DataAccessor.NoDataException("No content");
    return new FileEditorContents(document, element, project);
  }

  private boolean isSameFile(Document document, PsiElement element) {
    VirtualFile documentFile = FileDocumentManager.getInstance().getFile(document);
    PsiFile elementPsiFile = element.getContainingFile();
    VirtualFile elementFile = elementPsiFile != null ? elementPsiFile.getVirtualFile() : null;

    if (documentFile != null && documentFile.isValid() &&
        documentFile.equals(elementFile)) return true;
    return false;
  }

  private boolean checkSelection(DataContext dataContext) {
    VirtualFile[] virtualFiles = DataAccessor.VIRTUAL_FILE_ARRAY.from(dataContext);
    if (virtualFiles != null && virtualFiles.length != 1) return false;
    PsiElement[] psiElements = DataAccessor.PSI_ELEMENT_ARRAY.from(dataContext);
    if (psiElements != null && psiElements.length != 1) return false;
    return true;
  }

  protected void disableAction(Presentation presentation) {
    presentation.setVisible(false);
  }

  private static class FileEditorContents extends DiffRequest {
    private final PsiElement myPsiElement;
    private final Document myDocument;

    public FileEditorContents(Document document, PsiElement psiElement, Project project) {
      super(project);
      myDocument = document;
      myPsiElement = psiElement;
    }

    public String[] getContentTitles() {
      VirtualFile documentFile = getDocumentFile(myDocument);
      String documentTitle = documentFile != null ? ElementPresentation.forVirtualFile(documentFile).getNameWithFQComment() : "Editor";
      return new String[]{getElementPresentation().getNameWithFQComment(), documentTitle};
    }

    protected ElementPresentation getElementPresentation() {
      return ElementPresentation.forElement(myPsiElement);
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{DiffContentFactory.fromPsiElement(myPsiElement), DocumentContent.fromDocument(getProject(), myDocument)};
    }

    public String getWindowTitle() {
      return getElementPresentation().getQualifiedName() + " vs " + getContentTitle(myDocument);
    }
  }
}
