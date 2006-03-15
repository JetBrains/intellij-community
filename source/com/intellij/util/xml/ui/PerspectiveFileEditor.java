/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;

import java.beans.PropertyChangeListener;

/**
 * User: Sergey.Vasiliev
 */
abstract public class PerspectiveFileEditor extends UserDataHolderBase implements FileEditor {
  private final XmlFile myFile;
  private FileEditorManagerAdapter myFileEditorManagerAdapter;

  private Project myProject;


  protected PerspectiveFileEditor(final Project project, final VirtualFile file) {
    myProject = project;

    myFile = (XmlFile)PsiManager.getInstance(project).findFile(file);

    myFileEditorManagerAdapter = new FileEditorManagerAdapter() {
     public void selectionChanged(FileEditorManagerEvent event) {
       if (PerspectiveFileEditor.this.equals(event.getOldEditor())) {
         deselectNotify();
         if (event.getNewEditor() instanceof TextEditor) {
           setSelectionInTextEditor((TextEditor)event.getNewEditor(), getSelectedDomElement());
         }
       }
       else if (PerspectiveFileEditor.this.equals(event.getNewEditor())) {
         selectNotify();
         if (event.getOldEditor() instanceof TextEditor) {
           setSelectedDomElement(getSelectedDomElementFromTextEditor((TextEditor)event.getOldEditor()));
         } else if (event.getOldEditor() instanceof PerspectiveFileEditor) {
            setSelectedDomElement(((PerspectiveFileEditor)event.getOldEditor()).getSelectedDomElement());
         }
       }
     }
    };
    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myFileEditorManagerAdapter);
  }

  abstract protected DomElement getSelectedDomElement();
  abstract protected void setSelectedDomElement(DomElement domElement);

  protected DomElement getSelectedDomElementFromTextEditor(final TextEditor textEditor) {
    final PsiElement psiElement = myFile.findElementAt(textEditor.getEditor().getCaretModel().getOffset());

    if(psiElement == null) return null;

    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);

    return  DomManager.getDomManager(myFile.getProject()).getDomElement(xmlTag);
  }

  public void setSelectionInTextEditor(final TextEditor textEditor, final DomElement element) {
    if(element != null && element.isValid()&& element.getXmlTag()!=null) {
      final XmlTag tag = element.getXmlTag();
      final PsiFile file = tag.getContainingFile();
      if (file == null) return;

      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null || !document.equals(textEditor.getEditor().getDocument())) {
        return;
      }

      textEditor.getEditor().getCaretModel().moveToOffset(tag.getTextOffset());
      textEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  public XmlFile getXmlFile() {
    return myFile;
  }

  public void dispose() {
   FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myFileEditorManagerAdapter);
 }

  public boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(myFile.getVirtualFile());
  }

  public final boolean isValid() {
    return myFile.isValid();
  }

  public void selectNotify() {
  }

  public void deselectNotify() {
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return new FileEditorLocation() {
      public FileEditor getEditor() {
        return PerspectiveFileEditor.this;
      }

      public int compareTo(final FileEditorLocation fileEditorLocation) {
        return 0;
      }
    };
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }



}
