package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.XmlUtil;

public class PsiChangeHandler extends PsiTreeChangeAdapter {
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  public PsiChangeHandler(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
  }

  public void childAdded(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void beforeChildReplacement(PsiTreeChangeEvent event) {
    updateByChange(event.getOldChild());
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
    updateByChange(event.getOldParent());
    updateByChange(event.getNewParent());
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      myDaemonCodeAnalyzer.stopProcess(true);
    }
  }

  private void updateByChange(PsiElement child) {
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null) {
      EditorMarkupModel markupModel = (EditorMarkupModel) editor.getMarkupModel();
      markupModel.setErrorStripeRenderer(markupModel.getErrorStripeRenderer());
    }

    PsiFile file = child.getContainingFile();
    if (file == null) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      return;
    }
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;

    // optimization:
    PsiElement parent = child;
    while (true) {
      if (parent instanceof PsiFile || parent instanceof PsiDirectory) {
        myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
        return;
      }
      PsiElement pparent = parent.getParent();
      if(parent instanceof XmlTag){
        //if(!XmlUtil.isInAntBuildFile((XmlFile)parent.getContainingFile())){
          myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, pparent);
          return;
        //}
        //else{
        //  parent = parent.getContainingFile();
        //}
      }
      if (parent instanceof PsiCodeBlock
          && pparent instanceof PsiMethod
          && !((PsiMethod) pparent).isConstructor()
          && pparent.getParent() instanceof PsiClass
          && !(pparent.getParent() instanceof PsiAnonymousClass)) {
        // do not use this optimization for constructors and class initializers - to update non-initialized fields
        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, pparent);
        return;
      }
      parent = pparent;
    }
  }
}