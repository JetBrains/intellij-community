
package com.intellij.psi.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.lang.ASTNode;

import java.util.*;

public class PsiToDocumentSynchronizer extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiToDocumentSynchronizer");

  private final SmartPointerManagerImpl mySmartPointerManager;
  private PsiDocumentManagerImpl myPsiDocumentManager;

  public PsiToDocumentSynchronizer(PsiDocumentManagerImpl psiDocumentManager, SmartPointerManagerImpl smartPointerManager) {
    mySmartPointerManager = smartPointerManager;
    myPsiDocumentManager = psiDocumentManager;
  }

  public DocumentChangeTransaction getTransaction(final Document document) {
    return myTransactionsMap.get(document);
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

    boolean isOriginal = true;
    if(event.getParent() != null){
      ASTNode element = SourceTreeToPsiMap.psiElementToTree(event.getParent());
      while(element != null && !(element instanceof FileElement)) {
        element = element.getTreeParent();
      }
      PsiFile fileForDoc = PsiDocumentManager.getInstance(psiFile.getProject()).getPsiFile(document);

      isOriginal = element != null ? fileForDoc == SourceTreeToPsiMap.treeElementToPsi(element) : false;
      LOG.debug("DOCSync: " + isOriginal + "; document=" + document+"; file="+psiFile.getName() + ":" +
                psiFile.getClass() +"; file for doc="+fileForDoc.getName()+"; virtualfile="+psiFile.getVirtualFile());
    }

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


  private Map<Document, DocumentChangeTransaction> myTransactionsMap = new HashMap<Document, DocumentChangeTransaction>();

  public void replaceString(Document document, int startOffset, int endOffset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = myTransactionsMap.get(document);
    if(documentChangeTransaction != null) {
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, s);
    }
    else {
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
  }

  public void insertString(Document document, int offset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = myTransactionsMap.get(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(offset, 0, s);
    }
    else {
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
  }

  public void deleteString(Document document, int startOffset, int endOffset){
    final DocumentChangeTransaction documentChangeTransaction = myTransactionsMap.get(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, "");
    }
    else {
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
  }

  private DocumentEx getCachedDocument(PsiFile file) {
    return (DocumentEx)myPsiDocumentManager.getCachedDocument(file);
  }

  private TextBlock getTextBlock(Document document) {
    return myPsiDocumentManager.getTextBlock(document);
  }

  public void startTransaction(Document doc, PsiElement scope) {
    myTransactionsMap.put(doc, new DocumentChangeTransaction(doc, scope));
  }

  public void commitTransaction(Document document){
    final DocumentChangeTransaction documentChangeTransaction = myTransactionsMap.get(document);
    if(documentChangeTransaction == null) return;
    if(documentChangeTransaction.getTransactionRange() == null) return; // Nothing to do
    try{
      final PsiElement changeScope = documentChangeTransaction.getChangeScope();
      final PsiTreeChangeEventImpl fakeEvent = new PsiTreeChangeEventImpl(changeScope.getManager());
      fakeEvent.setParent(changeScope);
      fakeEvent.setFile(changeScope.getContainingFile());
      doSync(fakeEvent, new DocSyncAction() {
        public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
          doCommitTransaction(document, documentChangeTransaction);
        }
      });
    }
    finally{
      myTransactionsMap.remove(document);
    }
  }

  public void doCommitTransaction(final Document document){
    doCommitTransaction(document, myTransactionsMap.get(document));
  }

  private void doCommitTransaction(final Document document,
                                   final DocumentChangeTransaction documentChangeTransaction) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !document.isWritable();
      ex.setReadOnly(false);
      final List<Pair<TextRange, StringBuffer>> affectedFragments = documentChangeTransaction.getAffectedFragments();
      final Iterator<Pair<TextRange, StringBuffer>> iterator = affectedFragments.iterator();
      while (iterator.hasNext()) {
        final Pair<TextRange, StringBuffer> pair = iterator.next();
        final StringBuffer replaceBuffer = pair.getSecond();
        final TextRange range = pair.getFirst();
        if(replaceBuffer.length() == 0){
          ex.deleteString(range.getStartOffset(), range.getEndOffset());
        }
        else if(range.getLength() == 0){
          ex.insertString(range.getStartOffset(), replaceBuffer);
        }
        else{
          ex.replaceString(range.getStartOffset(),
                           range.getEndOffset(),
                           replaceBuffer);
        }
      }
      ex.setReadOnly(isReadOnly);
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  public void cancelTransaction(Document doc) {
    myTransactionsMap.remove(doc);
  }

  public class DocumentChangeTransaction{
    List<Pair<TextRange,StringBuffer>> myAffectedFragments = new ArrayList<Pair<TextRange, StringBuffer>>();
    private TextRange myTransactionRange = null;
    private final StringBuffer myTransactionBuffer = new StringBuffer();
    private final Document myDocument;
    private final PsiElement myChangeScope;

    public DocumentChangeTransaction(final Document doc, PsiElement scope) {
      myDocument = doc;
      myChangeScope = scope;
    }

    public TextRange getTransactionRange() {
      return myTransactionRange;
    }

    public List<Pair<TextRange, StringBuffer>> getAffectedFragments() {
      return myAffectedFragments;
    }

    public PsiElement getChangeScope() {
      return myChangeScope;
    }

    public StringBuffer getTransactionBuffer() {
      return myTransactionBuffer;
    }

    public void replace(int start, int length, String str){
      final int startInFragment;
      final StringBuffer fragmentReplaceText;

      { // calculating fragment
        { // minimize replace
          final int oldStart = start;
          int end = start + length;

          final int newStringLength = str.length();
          final String chars = getText(start, length + start);
          int newStartInString = 0;
          int newEndInString = newStringLength;
          {
            while (newStartInString < newStringLength &&
                   start < end &&
                   str.charAt(newStartInString) == chars.charAt(start - oldStart)) {
              start++;
              newStartInString++;
            }

            while (end > start &&
                   newEndInString > newStartInString &&
                   str.charAt(newEndInString - 1) == chars.charAt(end - oldStart - 1)) {
              newEndInString--;
              end--;
            }
          }

          str = str.substring(newStartInString, newEndInString);
          length = end - start;
        }

        final Pair<TextRange, StringBuffer> fragment = getFragmentByRange(start, length);
        fragmentReplaceText = fragment.getSecond();
        startInFragment = start - fragment.getFirst().getStartOffset();
      }

      fragmentReplaceText.replace(startInFragment, startInFragment + length, str);
    }

    private String getText(final int start, final int end) {
      int documentOffset = 0;
      int effectiveOffset = 0;
      StringBuffer text = new StringBuffer();
      Iterator<Pair<TextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
      while (iterator.hasNext() && effectiveOffset < end) {
        final Pair<TextRange, StringBuffer> pair = iterator.next();
        final TextRange range = pair.getFirst();
        final StringBuffer buffer = pair.getSecond();
        final int effectiveFragmentEnd = range.getStartOffset() + buffer.length();

        if(range.getStartOffset() <= start && effectiveFragmentEnd >= end){
          return buffer.substring(start - range.getStartOffset(), end - range.getStartOffset());
        }

        if(range.getStartOffset() >= start){
          final int effectiveStart = Math.max(effectiveOffset, start);
          text.append(myDocument.getChars(),
                                effectiveStart - effectiveOffset + documentOffset,
                                Math.min(range.getStartOffset(), end) - effectiveStart);
          if(end > range.getStartOffset()){
            text.append(buffer.substring(0, Math.min(end - range.getStartOffset(), buffer.length())));
          }
        }

        documentOffset += range.getEndOffset() - effectiveOffset;
        effectiveOffset = range.getStartOffset() + buffer.length();
      }

      if(effectiveOffset < end){
        final int effectiveStart = Math.max(effectiveOffset, start);
        text.append(myDocument.getChars(),
                              effectiveStart - effectiveOffset + documentOffset,
                              end - effectiveStart);
      }

      return text.toString();
    }

    private Pair<TextRange, StringBuffer> getFragmentByRange(int start, final int length) {
      final StringBuffer fragmentBuffer = new StringBuffer();
      int end = start + length;

      {
        // restoring buffer and remove all subfragments from the list
        int documentOffset = 0;
        int effectiveOffset = 0;

        Iterator<Pair<TextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
        while (iterator.hasNext() && effectiveOffset < end) {
          final Pair<TextRange, StringBuffer> pair = iterator.next();
          final TextRange range = pair.getFirst();
          final StringBuffer buffer = pair.getSecond();
          final int effectiveFragmentEnd = range.getStartOffset() + buffer.length();

          if(range.getStartOffset() <= start && effectiveFragmentEnd >= end) return pair;

          if(effectiveFragmentEnd >= start){
            final int effectiveStart = Math.max(effectiveOffset, start);
            if(range.getStartOffset() > start){
              fragmentBuffer.append(myDocument.getChars(),
                                    effectiveStart - effectiveOffset + documentOffset,
                                    Math.min(range.getStartOffset(), end) - effectiveStart);
            }
            if(end > range.getStartOffset()){
              fragmentBuffer.append(buffer);
              end = end > effectiveFragmentEnd ? end - (buffer.length() - range.getLength()) : range.getEndOffset();
              start = Math.min(start, range.getStartOffset());
              iterator.remove();
            }
          }

          documentOffset += range.getEndOffset() - effectiveOffset;
          effectiveOffset = effectiveFragmentEnd;
        }

        if(effectiveOffset < end){
          final int effectiveStart = Math.max(effectiveOffset, start);
          fragmentBuffer.append(myDocument.getChars(),
                                effectiveStart - effectiveOffset + documentOffset,
                                end - effectiveStart);
        }

      }

      final Pair<TextRange, StringBuffer> pair = new Pair<TextRange, StringBuffer>(new TextRange(start, end), fragmentBuffer);
      int i;
      for(i = 0; i < myAffectedFragments.size(); i++){
        if(start > pair.getFirst().getStartOffset()){
          myAffectedFragments.add(i, pair);
          break;
        }
      }
      if(i == myAffectedFragments.size()) myAffectedFragments.add(pair);

      return pair;
    }
  }
}
