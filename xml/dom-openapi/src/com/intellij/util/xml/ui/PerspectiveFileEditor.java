// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Sergey.Vasiliev
 */
public abstract class PerspectiveFileEditor extends UserDataHolderBase implements DocumentsEditor, Committable {
  private final Wrapper myWrapper = new Wrapper();
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Project myProject;
  private final VirtualFile myFile;
  private final UndoHelper myUndoHelper;

  private boolean myInitialised;
  private boolean myInitializing;  // `createCustomComponent()` is in progress
  private boolean myInvalidated;

  protected PerspectiveFileEditor(Project project, VirtualFile file) {
    myProject = project;
    myUndoHelper = new UndoHelper(project, this);
    myFile = file;

    project.getMessageBus().connect(this).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (!isValid()) return;

        ApplicationManager.getApplication().invokeLater(() -> {
          if (myUndoHelper.isShowing() && !getComponent().isShowing()) {
            deselectNotify();
          } else if (!myUndoHelper.isShowing() && getComponent().isShowing()) {
            selectNotify();
          }
        });

        FileEditor oldEditor = event.getOldEditor();
        FileEditor newEditor = event.getNewEditor();
        if (oldEditor == null || newEditor == null) return;
        if (oldEditor.getComponent().isShowing() && newEditor.getComponent().isShowing()) return;

        if (PerspectiveFileEditor.this.equals(oldEditor)) {
          if (newEditor instanceof TextEditor) {
            ensureInitialized();
            DomElement selectedDomElement = getSelectedDomElement();
            if (selectedDomElement != null) {
              setSelectionInTextEditor((TextEditor)newEditor, selectedDomElement);
            }
          }
        }
        else if (PerspectiveFileEditor.this.equals(newEditor)) {
          if (oldEditor instanceof TextEditor) {
            DomElement element = getSelectedDomElementFromTextEditor((TextEditor)oldEditor);
            if (element != null) {
              ensureInitialized();
              setSelectedDomElement(element);
            }
          }
          else if (oldEditor instanceof PerspectiveFileEditor) {
            ensureInitialized();
            DomElement selectedDomElement = ((PerspectiveFileEditor)oldEditor).getSelectedDomElement();
            if (selectedDomElement != null) {
              setSelectedDomElement(selectedDomElement);
            }
          }
        }
      }
    });

    myUndoHelper.startListeningDocuments();

    PsiFile psiFile = getPsiFile();
    if (psiFile != null) {
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document != null) {
        addWatchedDocument(document);
      }
    }
  }

  protected abstract @Nullable DomElement getSelectedDomElement();

  protected abstract void setSelectedDomElement(DomElement domElement);

  public final void addWatchedElement(@NotNull DomElement domElement) {
    addWatchedDocument(getDocumentManager().getDocument(DomUtil.getFile(domElement)));
  }

  final void removeWatchedElement(@NotNull DomElement domElement) {
    removeWatchedDocument(getDocumentManager().getDocument(DomUtil.getFile(domElement)));
  }

  private void addWatchedDocument(Document document) {
    myUndoHelper.addWatchedDocument(document);
  }

  private void removeWatchedDocument(Document document) {
    myUndoHelper.removeWatchedDocument(document);
  }

  private @Nullable DomElement getSelectedDomElementFromTextEditor(TextEditor textEditor) {
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) return null;
    PsiElement psiElement = psiFile.findElementAt(textEditor.getEditor().getCaretModel().getOffset());

    if (psiElement == null) return null;

    XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);

    return DomManager.getDomManager(myProject).getDomElement(xmlTag);
  }

  private void setSelectionInTextEditor(TextEditor textEditor, DomElement element) {
    if (element != null && element.isValid()) {
      XmlTag tag = element.getXmlTag();
      if (tag == null) return;

      PsiFile file = tag.getContainingFile();
      if (file == null) return;

      Document document = getDocumentManager().getDocument(file);
      if (document == null || !document.equals(textEditor.getEditor().getDocument())) return;

      textEditor.getEditor().getCaretModel().moveToOffset(tag.getTextOffset());
      textEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  protected final PsiDocumentManager getDocumentManager() {
    return PsiDocumentManager.getInstance(myProject);
  }

  public final @Nullable PsiFile getPsiFile() {
    return PsiManager.getInstance(myProject).findFile(myFile);
  }

  @Override
  public final @NotNull Document @NotNull [] getDocuments() {
    return myUndoHelper.getDocuments();
  }

  public final Project getProject() {
    return myProject;
  }

  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public void dispose() {
    if (myInvalidated) return;
    myInvalidated = true;
    myUndoHelper.stopListeningDocuments();
  }

  @Override
  public final boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(getVirtualFile());
  }

  @Override
  public boolean isValid() {
    return getVirtualFile().isValid();
  }

  @Override
  public void selectNotify() {
    if (!checkIsValid() || myInvalidated) return;
    ensureInitialized();
    setShowing(true);
    if (myInitialised) {
      reset();
    }
  }

  protected final void setShowing(boolean b) {
    myUndoHelper.setShowing(b);
  }

  protected final synchronized void ensureInitialized() {
    if (!isInitialised() && !myInitializing) {
      myInitializing = true;
      JComponent component = createCustomComponent();
      myWrapper.setContent(component);
      myInitialised = true;
    }
  }

  @Override
  public void deselectNotify() {
    if (!checkIsValid() || myInvalidated) return;
    setShowing(false);
    commit();
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    return new FileEditorLocation() {
      @Override
      public @NotNull FileEditor getEditor() {
        return PerspectiveFileEditor.this;
      }

      @Override
      public int compareTo(@NotNull FileEditorLocation fileEditorLocation) {
        return 0;
      }
    };
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public void setState(@NotNull FileEditorState state) { }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  protected boolean checkIsValid() {
    if (!myInvalidated && !isValid()) {
      myInvalidated = true;
      myPropertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, Boolean.TRUE, Boolean.FALSE);
    }
    return !myInvalidated;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return getWrapper();
  }

  protected abstract @NotNull JComponent createCustomComponent();

  public Wrapper getWrapper() {
    return myWrapper;
  }

  protected final synchronized boolean isInitialised() {
    return myInitialised;
  }
}
