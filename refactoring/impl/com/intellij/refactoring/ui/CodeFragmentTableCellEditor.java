package com.intellij.refactoring.ui;

import com.intellij.codeInsight.daemon.impl.JavaReferenceImporter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

/**
 * @author dsl
 */
public class CodeFragmentTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private Document myDocument;
  protected PsiCodeFragment myCodeFragment;
  private Project myProject;
  private EditorTextField myEditorTextField;

  public CodeFragmentTableCellEditor(final Project project) {
    myProject = project;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    myCodeFragment = (PsiCodeFragment)value;

    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myCodeFragment);
    myEditorTextField = new EditorTextField(myDocument, myProject, StdFileTypes.JAVA) {
      protected boolean shouldHaveBorder() {
        return false;
      }
    };
    return myEditorTextField;
  }

  public PsiCodeFragment getCellEditorValue() {
    return myCodeFragment;
  }

  public void cancelCellEditing() {
    super.cancelCellEditing();
  }

  public boolean stopCellEditing() {
    final Editor editor = myEditorTextField.getEditor();
    if (editor != null) {
      JavaReferenceImporter.autoImportReferenceAtCursor(editor, myCodeFragment, true);
    }
    super.stopCellEditing();
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    return true;
  }
}
