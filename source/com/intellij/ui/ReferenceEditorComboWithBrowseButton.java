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
public class ReferenceEditorComboWithBrowseButton extends ComponentWithBrowseButton<EditorComboBox> implements TextAccessor {
  public ReferenceEditorComboWithBrowseButton(final ActionListener browseActionListener,
                                         final String text,
                                         final PsiManager manager,
                                         boolean toAcceptClasses) {
    super(new EditorComboBox(createDocument(text, manager, toAcceptClasses), manager.getProject(), StdFileTypes.JAVA), browseActionListener);
  }

  private static Document createDocument(final String text, PsiManager manager, boolean isClassesAccepted) {
    PsiPackage defaultPackage = manager.findPackage("");
    final PsiCodeFragment fragment = manager.getElementFactory().createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setEverythingAcessible(true);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public String getText(){
    return getChildComponent().getText().trim();
  }

  public void setText(final String text){
    getChildComponent().setText(text);
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    Dimension size = getChildComponent().getPreferredSize();
    FontMetrics fontMetrics = getChildComponent().getFontMetrics(getChildComponent().getFont());
    size.width = fontMetrics.charWidth('a') * charCount;
    getChildComponent().setPreferredSize(size);
  }

  public boolean isEditable() {
    return !getChildComponent().getEditorEx().isViewer();
  }

  public void setHistory(String[] history) {
    getChildComponent().setHistory(history);
  }

}
