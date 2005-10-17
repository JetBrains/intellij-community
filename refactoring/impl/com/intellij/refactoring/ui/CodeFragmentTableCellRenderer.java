package com.intellij.refactoring.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author dsl
 */
public class CodeFragmentTableCellRenderer implements TableCellRenderer {
  private final Project myProject;

  public CodeFragmentTableCellRenderer(Project project) {
    myProject = project;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    PsiCodeFragment codeFragment = (PsiCodeFragment)value;

    if (codeFragment != null) {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(codeFragment);
      return new EditorTextField(document, myProject, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
    }
    else {
      return new EditorTextField("", myProject, StdFileTypes.JAVA) {
        protected boolean shouldHaveBorder() {
          return false;
        }        
      };
    }
  }
}
