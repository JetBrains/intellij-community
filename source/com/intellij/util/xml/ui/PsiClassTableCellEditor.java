/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.xml.DomElement;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

/**
 * @author peter
 */
public class PsiClassTableCellEditor extends AbstractTableCellEditor {
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;
  private EditorTextField myEditor;

  public PsiClassTableCellEditor(DomElement element) {
    this(element.getManager().getProject(), element.getResolveScope());
  }

  public PsiClassTableCellEditor(final Project project, final GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  public final Object getCellEditorValue() {
    return myEditor.getText();
  }

  public final boolean stopCellEditing() {
    final boolean b = super.stopCellEditing();
    myEditor = null;
    return b;
  }

  public boolean isCellEditable(EventObject e) {
    return !(e instanceof MouseEvent) || ((MouseEvent)e).getClickCount() >= 2;
  }

  public final Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final Document document = ReferenceEditorWithBrowseButton.createDocument(value == null ? "" : (String)value, PsiManager.getInstance(myProject), true);
    myEditor = new EditorTextField(document, myProject, StdFileTypes.JAVA){
      protected boolean shouldHaveBorder() {
        return false;
      }
    };
    final JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(myEditor);
    final FixedSizeButton button = new FixedSizeButton(myEditor);
    panel.add(button, BorderLayout.EAST);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
          .createInheritanceClassChooser(UIBundle.message("choose.class"), mySearchScope, null, true, true, Condition.TRUE);
        chooser.showDialog();
        final PsiClass psiClass = chooser.getSelectedClass();
        if (psiClass != null) {
          myEditor.setText(psiClass.getQualifiedName());
        }
      }
    });
    panel.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        if (!e.isTemporary() && myEditor != null) {
          myEditor.requestFocus();
        }
      }

      public void focusLost(FocusEvent e) {
      }
    });
    myEditor.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
      }

      public void focusLost(FocusEvent e) {
        if (!e.isTemporary()) {
          stopCellEditing();
        }
      }
    });

    //ComponentWithBrowseButton.MyDoClickAction.addTo(button, myEditor);

    return panel;
  }
}
