/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.Function;

import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor{
  private final Function<String,Document> myFactory;

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

  public void setText(final String text){
    getEditorTextField().setDocument(myFactory.fun(text));
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    Dimension size = getEditorTextField().getPreferredSize();
    FontMetrics fontMetrics = getEditorTextField().getFontMetrics(getEditorTextField().getFont());
    size.width = fontMetrics.charWidth('a') * charCount;
    getEditorTextField().setPreferredSize(size);
  }

  public boolean isEditable() {
    return !getEditorTextField().getEditor().isViewer();
  }
}
