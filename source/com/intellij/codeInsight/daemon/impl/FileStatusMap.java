
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.WeakHashMap;

import java.util.Iterator;
import java.util.Map;

public class FileStatusMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.FileStatusMap");

  private final Project myProject;

  private Map myDocumentToStatusMap = new WeakHashMap(); // Document --> FileStatus, all dirty if absent

  private final Key<RefCountHolder> REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY = Key.create("DaemonCodeAnalyzerImpl.REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY");
  private Map myDocumentsWithRefCountHolders = new WeakHashMap(); // Document --> null
  private final Object myRefCountHolderLock = new Object();

  private static class FileStatus {
    PsiElement dirtyScope; //Q: use WeakReference?
    PsiElement overridenDirtyScope;
    PsiElement localInspectionsDirtyScope;

    FileStatus(PsiElement dirtyScope, PsiElement overridenDirtyScope, PsiElement inspectionDirtyScope) {
      this.dirtyScope = dirtyScope;
      this.overridenDirtyScope = overridenDirtyScope;
      this.localInspectionsDirtyScope = inspectionDirtyScope;
    }
  }

  public static final int NORMAL_HIGHLIGHTERS = 1;
  public static final int OVERRIDEN_MARKERS = 2;
  public static final int LOCAL_INSPECTIONS = 3;

  public FileStatusMap(Project project) {
    myProject = project;
  }

  public void markAllFilesDirty() {
    synchronized(myDocumentToStatusMap){
      myDocumentToStatusMap.clear();
    }

    synchronized(myRefCountHolderLock){
      for(Iterator iterator = myDocumentsWithRefCountHolders.keySet().iterator(); iterator.hasNext();){
        Document document = (Document)iterator.next();
        document.putUserData(REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY, null);
      }
      myDocumentsWithRefCountHolders.clear();
    }
  }

  public void markFileDirty(Document document) {
    synchronized(myDocumentToStatusMap){
      myDocumentToStatusMap.remove(document);
    }

    synchronized(myRefCountHolderLock){
      document.putUserData(REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY, null);
      myDocumentsWithRefCountHolders.remove(document);
    }
  }

  public void markFileUpToDate(Document document, int part) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = (FileStatus)myDocumentToStatusMap.get(document);
      if (status == null){
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status = new FileStatus(file, file, file);
        myDocumentToStatusMap.put(document, status);
      }

      if (part == NORMAL_HIGHLIGHTERS){
        status.dirtyScope = null;
      }
      else if (part == OVERRIDEN_MARKERS){
        status.overridenDirtyScope = null;
      }
      else if (part == LOCAL_INSPECTIONS){
        status.localInspectionsDirtyScope = null;
      }
      else{
        LOG.assertTrue(false);
      }
    }
  }

  public PsiElement getFileDirtyScope(Document document, int part) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = (FileStatus)myDocumentToStatusMap.get(document);
      if (status == null){
        return PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      }
      if (part == NORMAL_HIGHLIGHTERS){
        return status.dirtyScope;
      }
      else if (part == OVERRIDEN_MARKERS){
        return status.overridenDirtyScope;
      }
      else if (part == LOCAL_INSPECTIONS) {
        return status.localInspectionsDirtyScope;
      }
      else{
        LOG.assertTrue(false);
        return null;
      }
    }
  }

  public void markFileScopeDirty(Document document, PsiElement scope) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = (FileStatus)myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      status.dirtyScope = combineScopes(status.dirtyScope, scope);
      status.localInspectionsDirtyScope = combineScopes(status.localInspectionsDirtyScope, scope);
      //status.overridenDirtyScope = combineScopes(status.overridenDirtyScope, scope);
    }
  }

  private PsiElement combineScopes(PsiElement scope1, PsiElement scope2) {
    if (scope1 == null) return scope2;
    if (scope2 == null) return scope1;
    return PsiTreeUtil.findCommonParent(scope1, scope2);
  }

  public RefCountHolder getRefCountHolder(Document document, PsiFile file) {
    RefCountHolder refCountHolder;
    synchronized (myRefCountHolderLock) {
      refCountHolder = (RefCountHolder)document.getUserData(REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY);
      if (refCountHolder == null) {
        refCountHolder = new RefCountHolder(file);
        document.putUserData(REF_COUND_HOLDER_IN_EDITOR_DOCUMENT_KEY, refCountHolder);
        myDocumentsWithRefCountHolders.put(document, null);
      }
    }
    return refCountHolder;
  }
}