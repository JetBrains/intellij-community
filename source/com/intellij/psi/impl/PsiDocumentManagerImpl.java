package com.intellij.psi.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.text.CharArrayUtil;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//todo listen & notifyListeners readonly events?

public class PsiDocumentManagerImpl extends PsiDocumentManager implements ProjectComponent, DocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiDocumentManagerImpl");

  private final Project myProject;
  private PsiManager myPsiManager;
  private final Key<PsiFile> KEY_PSI_FILE = Key.create("KEY_PSI_FILE");
  private final Key<WeakReference<Document>> KEY_DOCUMENT = Key.create("KEY_DOCUMENT");
  private final Key<TextBlock> KEY_TEXT_BLOCK = Key.create("KEY_TEXT_BLOCK");
  private Set<Document> myUncommittedDocuments = new HashSet<Document>();
  private boolean myProcessDocumentEvents = true;
  private SmartPointerManagerImpl mySmartPointerManager;
  private BlockSupportImpl myBlockSupport;
  private boolean myIsCommitInProgress;

  private List<Listener> myListeners = new ArrayList<Listener>();
  private Listener[] myCachedListeners = null;

  public PsiDocumentManagerImpl(Project project,
                                PsiManager psiManager,
                                SmartPointerManager smartPointerManager,
                                BlockSupport blockSupport, EditorFactory editorFactory) {
    myProject = project;

    myPsiManager = psiManager;
    mySmartPointerManager = (SmartPointerManagerImpl)smartPointerManager;
    myBlockSupport = (BlockSupportImpl)blockSupport;
    myPsiManager.addPsiTreeChangeListener(new PsiToDocumentSynchronizer(this, mySmartPointerManager));
    editorFactory.getEventMulticaster().addDocumentListener(this);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "PsiDocumentManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(this);
  }

  public PsiFile getPsiFile(Document document) {
    PsiFile psiFile = getCachedPsiFile(document);

    if (psiFile == null){
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile == null || !virtualFile.isValid()) return null;
      psiFile = getPsiFile(virtualFile);
      if (psiFile == null) return null;

      psiFile.setModificationStamp(document.getModificationStamp());
      fireFileCreated(document, psiFile);
    }

    return psiFile;
  }


  public PsiFile getCachedPsiFile(Document document) {
    PsiFile file = document.getUserData(KEY_PSI_FILE);
    if (file != null) {
      /* TODO[max, lex]: This is right code. Disabled for now since Lex wants to get event invalid files through this hole.
      if (!file.isValid()) {
        document.putUserData(KEY_PSI_FILE, null);
        return null;
      }
      */
      return file;
    }

    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    PsiFile psiFile = getCachedPsiFile(virtualFile);
    return psiFile;
  }

  protected PsiFile getCachedPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerImpl)myPsiManager).getFileManager().getCachedPsiFile(virtualFile);
  }

  protected PsiFile getPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerImpl)myPsiManager).getFileManager().findFile(virtualFile);
  }

  public Document getDocument(PsiFile file) {
    if (file instanceof PsiBinaryFile) return null;

    Document document = getCachedDocument(file);
    if (document != null) return document;

    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null){
      document = FileDocumentManager.getInstance().getDocument(virtualFile);
    }
    else{
      if (!file.isPhysical()) return null;
      document = createDocument(file.getText());
      ((DocumentEx)document).setModificationStamp(file.getModificationStamp());

      file.putUserData(KEY_DOCUMENT, new WeakReference<Document>(document));
      document.putUserData(KEY_PSI_FILE, file);
    }

    fireDocumentCreated(document, file);

    return document;
  }

  private static Document createDocument(String text) {
    return EditorFactory.getInstance().createDocument(text);
  }

  public Document getCachedDocument(PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null){
      return FileDocumentManager.getInstance().getCachedDocument(vFile);
    }
    else{
      WeakReference<Document> reference = file.getUserData(KEY_DOCUMENT);
      if (reference == null) return null;
      return reference.get();
    }
  }

  public void commitAllDocuments() {
    if (myUncommittedDocuments.isEmpty()) return;

    //long time1 = System.currentTimeMillis();

    final Document[] documents = getUncommittedDocuments();
    for(int i = 0; i < documents.length; i++){
      Document document = documents[i];
      commitDocument(document);
    }

    //long time2 = System.currentTimeMillis();
    //Statistics.commitTime += (time2 - time1);
  }

  public void commitDocument(final Document document) {
    ApplicationManager.getApplication().runWriteAction(
      new CommitToPsiFileAction() {
        public void run() {
          if (!isUncommited(document)) return;
          myUncommittedDocuments.remove(document);

          final PsiFile file = getCachedPsiFile(document);
          if (file == null || !file.isValid()) return;
          /* This is right code. Commented out due TODO in getCachedPsiFile()
          if (file == null) return;
          LOG.assertTrue(file.isValid());
          */

          commit(document, file);
        }
      }
    );
  }

  public <T> T commitAndRunReadAction(final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    commitAndRunReadAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public void commitAndRunReadAction(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (SwingUtilities.isEventDispatchThread()){
      commitAllDocuments();
      runnable.run();
    }
    else{
      LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed(), "Don't call commitAndRunReadAction inside ReadAction it may cause a deadlock.");

      final Semaphore s1 = new Semaphore();
      final Semaphore s2 = new Semaphore();
      final boolean[] commited = {false};

      application.runReadAction(
        new Runnable() {
          public void run() {
            if (myUncommittedDocuments.isEmpty()){
              runnable.run();
              commited[0] = true;
            }
            else{
              s1.down();
              s2.down();
              final Runnable commitRunnable = new Runnable() {
                public void run() {
                  commitAllDocuments();
                  s1.up();
                  s2.waitFor();
                }
              };
              final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
              if (progressIndicator == null) {
                ApplicationManager.getApplication().invokeLater(commitRunnable);
              }
              else {
                ApplicationManager.getApplication().invokeLater(commitRunnable, progressIndicator.getModalityState());
              }
            }
          }
        }
      );

      if (!commited[0]){
        s1.waitFor();
        application.runReadAction(
          new Runnable() {
            public void run() {
              s2.up();
              runnable.run();
            }
          }
        );
      }
    }
  }

  private Listener[] getCachedListeners() {
    synchronized (myListeners) {
      if (myCachedListeners == null) {
        myCachedListeners = myListeners.toArray(new Listener[myListeners.size()]);
      }
      return myCachedListeners;
    }
  }

  public void addListener(Listener listener) {
    synchronized (myListeners) {
      myListeners.add(listener);
      myCachedListeners = null;
    }
  }

  public void removeListener(Listener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
      myCachedListeners = null;
    }
  }

  protected void fireDocumentCreated(Document document, PsiFile file) {
    Listener[] listeners = getCachedListeners();
    for (int i = 0; i < listeners.length; i++) {
      Listener listener = listeners[i];
      listener.documentCreated(document, file);
    }
  }

  protected void fireFileCreated(Document document, PsiFile file) {
    Listener[] listeners = getCachedListeners();
    for (int i = 0; i < listeners.length; i++) {
      Listener listener = listeners[i];
      listener.fileCreated(file, document);
    }
  }

  protected void commit(final Document document, final PsiFile file) {
    document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, null);

    TextBlock textBlock = getTextBlock(document);
    if (textBlock.isEmpty()) return;

    myIsCommitInProgress = true;
    try{
      if (file.getModificationStamp() != document.getModificationStamp()){
        if (mySmartPointerManager != null) { // can be true in "mock" tests
          mySmartPointerManager.synchronizePointers(file);
        }

        CompositeElement treeElement = ((PsiFileImpl)file).calcTreeElement(); // Lock up in local variable so gc wont collect it.
        textBlock = getTextBlock(document);
        if (textBlock.isEmpty()) return; // if tree was just loaded above textBlock will be cleared by contentsLoaded

        char[] chars = CharArrayUtil.fromSequence(document.getCharsSequence());
        int startOffset = textBlock.getStartOffset();
        int endOffset = textBlock.getTextEndOffset();
        final int psiEndOffset = textBlock.getPsiEndOffset();
        //String s = new String(chars, startOffset, endOffset - startOffset);
        //myBlockSupport.reparseRangeInternal(file, startOffset, psiEndOffset, s);
        myBlockSupport.reparseRange(file, startOffset, psiEndOffset, endOffset - psiEndOffset, chars);
        //checkConsistency(file, document);
        file.setModificationStamp(document.getModificationStamp());
      }

      textBlock.clear();
    }
    finally{
      myIsCommitInProgress = false;
    }

    //mySmartPointerManager.synchronizePointers(file);
  }

  public Document[] getUncommittedDocuments() {
    return myUncommittedDocuments.toArray(new Document[myUncommittedDocuments.size()]);
  }

  public boolean isUncommited(Document document) {
    return myUncommittedDocuments.contains(document);
  }

  public boolean hasUncommitedDocuments() {
    if (myIsCommitInProgress) return false;
    return !myUncommittedDocuments.isEmpty();
  }

  public void setProcessDocumentEvents(boolean processDocumentEvents) {
    myProcessDocumentEvents = processDocumentEvents;
  }

  private final Key<CompositeElement> TEMP_TREE_IN_DOCUMENT_KEY = Key.create("TEMP_TREE_IN_DOCUMENT_KEY");

  public void beforeDocumentChange(DocumentEvent event) {
    if (!myProcessDocumentEvents) return;

    final Document document = event.getDocument();
    final PsiFile file = getCachedPsiFile(document);
    if (file == null) return;

    if (file instanceof PsiFileImpl){
      myIsCommitInProgress = true;
      try{
        PsiFileImpl psiFile = (PsiFileImpl)file;
        // tree should be initialized and be kept until commit
        document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, psiFile.calcTreeElement());
      }
      finally{
        myIsCommitInProgress = false;
      }
    }
    if (mySmartPointerManager != null) { // can be false in "mock" tests
      mySmartPointerManager.fastenBelts(file);
    }
  }

  public void documentChanged(DocumentEvent event) {
    if (!myProcessDocumentEvents) return;

    final Document document = event.getDocument();
    final PsiFile file = getCachedPsiFile(document);
    if (file == null) return;

    if (mySmartPointerManager != null) { // can be false in "mock" tests
      mySmartPointerManager.unfastenBelts(file);
    }

    getTextBlock(document).documentChanged(event);
    myUncommittedDocuments.add(document);

    if (ApplicationManager.getApplication().getCurrentWriteAction(PsiExternalChangeAction.class) != null){
      commitDocument(document);
    }
  }

  public TextBlock getTextBlock(Document document) {
    TextBlock textBlock = document.getUserData(KEY_TEXT_BLOCK);
    if (textBlock == null){
      textBlock = new TextBlock();
      document.putUserData(KEY_TEXT_BLOCK, textBlock);
    }

    return textBlock;
  }

  public static void checkConsistency(PsiFile psiFile, Document document) {
    //todo hack
    if (psiFile.getVirtualFile() == null) return;

    CharSequence editorText = document.getCharsSequence();
    int documentLength = document.getTextLength();
    if (psiFile.textMatches(editorText)) {
      LOG.assertTrue(psiFile.getTextLength() == documentLength);
      LOG.debug("Consistent OK: length=" + documentLength + "; file=" + psiFile.getName() + ":" + psiFile.getClass());
      return;
    }

    char[] fileText = psiFile.textToCharArray();
    LOG.error("File text mismatch after reparse. File length="+fileText.length+"; Doc length="+documentLength);
    int i = 0;
    for(; i < documentLength; i++){
      if (i >= fileText.length){
        LOG.info("editorText.length > psiText.length i=" + i);
        break;
      }
      if (editorText.charAt(i) != fileText[i]){
        LOG.info("first unequal char i=" + i);
        break;
      }
    }
    LOG.info("*********************************************");
    if (i <= 500){
      LOG.info("Equal part:" + editorText.subSequence(0, i));
    }
    else{
      LOG.info("Equal part start:\n" + editorText.subSequence(0, 200));
      LOG.info("................................................");
      LOG.info("................................................");
      LOG.info("................................................");
      LOG.info("Equal part end:\n" + editorText.subSequence(i - 200, i));
    }
    LOG.info("*********************************************");
    LOG.info("Editor Text tail:\n" + editorText.subSequence(i, Math.min(i + 300, documentLength)));
    LOG.info("*********************************************");
    LOG.info("Psi Text tail:\n" + new String(fileText, i, Math.min(i + 300, fileText.length) - i));
    LOG.info("*********************************************");
    document.replaceString(0, documentLength, psiFile.getText());
  }

  public void contentsLoaded(PsiFileImpl file) {
    final Document document = getCachedDocument(file);
    LOG.assertTrue(document != null);
    getTextBlock(document).clear();
  }

  public boolean isDocumentCommited(Document doc) {
    if (myIsCommitInProgress) return true;
    return !myUncommittedDocuments.contains(doc);
  }
}
