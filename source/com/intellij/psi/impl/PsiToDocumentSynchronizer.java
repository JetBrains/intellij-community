
package com.intellij.psi.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;

class PsiToDocumentSynchronizer extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiToDocumentSynchronizer");

  private final SmartPointerManagerImpl mySmartPointerManager;
  private PsiDocumentManagerImpl myPsiDocumentManager;

  public PsiToDocumentSynchronizer(PsiDocumentManagerImpl psiDocumentManager, SmartPointerManagerImpl smartPointerManager) {
    mySmartPointerManager = smartPointerManager;
    myPsiDocumentManager = psiDocumentManager;
  }

  private static interface DocSyncAction {
    void syncDocument(Document document, PsiTreeChangeEventImpl event);
  }
  private void doSync(PsiTreeChangeEvent event, DocSyncAction syncAction) {
    if (!toProcessPsiEvent()) {
      return;
    }
    PsiFile psiFile = event.getFile();
    if (psiFile == null) return;
    DocumentEx document = getCachedDocument(psiFile);
    if (document == null) return;

    TextBlock textBlock = getTextBlock(document);
    if (!textBlock.isEmpty()) {
      LOG.error("Attempt to modify PSI for non-commited Document!");
      textBlock.clear();
    }

    TreeElement element = SourceTreeToPsiMap.psiElementToTree(event.getParent());
    while(element != null && !(element instanceof FileElement)) {
      element = element.getTreeParent();
    }
    PsiFile fileForDoc = PsiDocumentManager.getInstance(psiFile.getProject()).getPsiFile(document);
    boolean isOriginal = element != null ? fileForDoc == SourceTreeToPsiMap.treeElementToPsi(element) : false;
    LOG.debug("DOCSync: " + isOriginal + "; document=" + document+"; file="+psiFile.getName() + ":" +
        psiFile.getClass() +"; file for doc="+fileForDoc.getName()+"; virtualfile="+psiFile.getVirtualFile());

    if (isOriginal) {
      myPsiDocumentManager.setProcessDocumentEvents(false);
      syncAction.syncDocument(document, (PsiTreeChangeEventImpl)event);
      document.setModificationStamp(psiFile.getModificationStamp());
      myPsiDocumentManager.setProcessDocumentEvents(true);
      mySmartPointerManager.synchronizePointers(psiFile);
      if (LOG.isDebugEnabled()) {
        PsiDocumentManagerImpl.checkConsistency(psiFile, document);
        if (psiFile instanceof JspxFileImpl) {
            ( (JspxFileImpl)psiFile).checkAllConsistent();
        }
      }
    }
  }

  public void childAdded(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        insertString(document, event.getOffset(), event.getChild().getText());
      }
    });
  }

  public void childRemoved(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        deleteString(document, event.getOffset(), event.getOffset() + event.getOldLength());
      }
    });
  }

  public void childReplaced(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getNewChild().getText());
      }
    });
  }

  public void childrenChanged(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getParent().getText());
      }
    });
  }

  public void beforeChildReplacement(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  public void beforeChildAddition(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  public void beforeChildRemoval(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  private void processBeforeEvent(PsiTreeChangeEvent event) {
    if (toProcessPsiEvent()) {
      PsiFile psiFile = event.getParent().getContainingFile();
      if (psiFile == null) return;

      //TODO: get red of this?
      mySmartPointerManager.fastenBelts(psiFile);
      mySmartPointerManager.unfastenBelts(psiFile);
    }
  }

  private static boolean toProcessPsiEvent() {
    Application application = ApplicationManager.getApplication();
    return application.getCurrentWriteAction(CommitToPsiFileAction.class) == null
           && application.getCurrentWriteAction(PsiExternalChangeAction.class) == null;
  }

  private static void replaceString(Document document, int startOffset, int endOffset, String s) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !document.isWritable();
      ex.setReadOnly(false);
      ex.replaceString(startOffset, endOffset, s);
      ex.setReadOnly(isReadOnly);
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  private static void insertString(Document document, int offset, String s) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !ex.isWritable();
      ex.setReadOnly(false);
      ex.insertString(offset, s);
      ex.setReadOnly(isReadOnly);
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  private static void deleteString(Document document, int startOffset, int endOffset){
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !ex.isWritable();
      ex.setReadOnly(false);
      ex.deleteString(startOffset, endOffset);
      ex.setReadOnly(isReadOnly);
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  private DocumentEx getCachedDocument(PsiFile file) {
    return (DocumentEx)myPsiDocumentManager.getCachedDocument(file);
  }

  private TextBlock getTextBlock(Document document) {
    return myPsiDocumentManager.getTextBlock(document);
  }
}