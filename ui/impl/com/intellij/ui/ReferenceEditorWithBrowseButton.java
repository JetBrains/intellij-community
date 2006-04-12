/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;

import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor{
  private final Factory<Document> myFactory;

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final Project project, final Factory<Document> factory) {
    super(new EditorTextField(factory.create(), project, StdFileTypes.JAVA), browseActionListener);
    myFactory = factory;
  }

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                         final String text,
                                         final PsiManager manager,
                                         final boolean toAcceptClasses) {
    this(browseActionListener, manager.getProject(), new Factory<Document>() {
      public Document create() {
        return createDocument(text, manager, toAcceptClasses);
      }
    });
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
    getEditorTextField().setDocument(myFactory.create());
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
