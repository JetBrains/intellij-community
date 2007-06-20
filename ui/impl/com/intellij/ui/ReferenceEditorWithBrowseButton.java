/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.Function;

import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor{
  private final Function<String,Document> myFactory;
  private List<DocumentListener> myDocumentListeners = new CopyOnWriteArrayList<DocumentListener>();

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final Project project, final Function<String,Document> factory, String text) {
    this(browseActionListener, new EditorTextField(factory.fun(text), project, StdFileTypes.JAVA), factory);
  }

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final EditorTextField editorTextField, final Function<String,Document> factory) {
    super(editorTextField, browseActionListener);
    myFactory = factory;
  }

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                         final String text,
                                         final PsiManager manager,
                                         final boolean toAcceptClasses) {
    this(browseActionListener, manager.getProject(), new Function<String,Document>() {
      public Document fun(final String s) {
        return createDocument(s, manager, toAcceptClasses);
      }
    }, text);
  }

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    getEditorTextField().getDocument().addDocumentListener(listener);
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    getEditorTextField().getDocument().removeDocumentListener(listener);
  }

  public static Document createDocument(final String text, PsiManager manager, boolean isClassesAccepted) {
    PsiPackage defaultPackage = manager.findPackage("");
    final PsiCodeFragment fragment = manager.getElementFactory().createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(PsiCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public static Document createTypeDocument(final String text, PsiManager manager) {
    PsiPackage defaultPackage = manager.findPackage("");
    final PsiCodeFragment fragment = manager.getElementFactory().createTypeCodeFragment(text, defaultPackage, false, true, false);
    fragment.setVisibilityChecker(PsiCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public EditorTextField getEditorTextField() {
    return getChildComponent();
  }

  public String getText(){
    return getEditorTextField().getText().trim();
  }

  public void setText(final String text) {
    Document oldDocument = getEditorTextField().getDocument();
    String oldText = oldDocument.getText();
    for(DocumentListener listener: myDocumentListeners) {
      oldDocument.removeDocumentListener(listener);
    }
    Document document = myFactory.fun(text);
    getEditorTextField().setDocument(document);
    for(DocumentListener listener: myDocumentListeners) {
      document.addDocumentListener(listener);
      listener.documentChanged(new DocumentEventImpl(document, 0, oldText, text, -1));
    }
  }

  public boolean isEditable() {
    return !getEditorTextField().getEditor().isViewer();
  }
}
