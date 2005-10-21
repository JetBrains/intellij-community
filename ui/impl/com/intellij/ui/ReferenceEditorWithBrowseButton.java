package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
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
  private final PsiManager myManager;
  private final boolean myToAcceptClasses;

  public ReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                         final String text,
                                         final PsiManager manager,
                                         boolean toAcceptClasses) {
    super(new EditorTextField(createDocument(text, manager, toAcceptClasses), manager.getProject(), StdFileTypes.JAVA), browseActionListener);
    myManager = manager;
    myToAcceptClasses = toAcceptClasses;
  }

  private static Document createDocument(final String text, PsiManager manager, boolean isClassesAccepted) {
    PsiPackage defaultPackage = manager.findPackage("");
    final PsiCodeFragment fragment = manager.getElementFactory().createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
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
    getEditorTextField().setDocument(createDocument(text, myManager, myToAcceptClasses));
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
