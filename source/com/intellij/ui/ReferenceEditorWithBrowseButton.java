package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;

import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class ReferenceEditorWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> {
  private final PsiManager myManager;

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener, final String text, final PsiManager manager) {
    super(new EditorTextField(createDocument(text, manager), manager.getProject(), StdFileTypes.JAVA), browseActionListener);
    myManager = manager;
  }

  private static Document createDocument(final String text, PsiManager manager) {
    final PsiCodeFragment fragment = manager.getElementFactory().createReferenceCodeFragment(text, null, true);
    fragment.setEverythingAcessible(true);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public EditorTextField getEditorTextField() {
    return getChildComponent();
  }

  public String getText(){
    return getEditorTextField().getText().trim();
  }

  public void setText(final String text){
    getEditorTextField().setDocument(createDocument(text, myManager));
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
