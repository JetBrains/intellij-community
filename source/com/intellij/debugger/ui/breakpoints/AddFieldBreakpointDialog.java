/*
 * @author: Eugene Zhuravlev
 * Date: Sep 11, 2002
 * Time: 5:23:47 PM
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

abstract class AddFieldBreakpointDialog extends DialogWrapper {
  private final Project myProject;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFieldChooser;
  private TextFieldWithBrowseButton myClassChooser;

  public AddFieldBreakpointDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle("Add Field Watchpoint");
    init();
  }

  protected JComponent createCenterPanel() {
    myClassChooser.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateUI();
      }
    });

    myClassChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PsiClass currentClass = getSelectedClass();
        TreeClassChooserDialog chooser = new TreeClassChooserDialog("Choose Field's class", myProject, GlobalSearchScope.allScope(myProject), null, null);
        if (currentClass != null) {
          PsiFile containingFile = currentClass.getContainingFile();
          if (containingFile != null) {
            PsiDirectory containingDirectory = containingFile.getContainingDirectory();
            if (containingDirectory != null) {
              chooser.selectDirectory(containingDirectory);
            }
          }
        }
        chooser.show();
        PsiClass selectedClass = chooser.getSelectedClass();
        if (selectedClass != null) {
          myClassChooser.setText(selectedClass.getQualifiedName());
        }
      }
    });

    myFieldChooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PsiClass selectedClass = getSelectedClass();
        if (selectedClass != null) {
          PsiField[] fields = selectedClass.getFields();
          MemberChooser chooser = new MemberChooser(fields, false, false, myProject);
          chooser.setTitle(fields.length > 0 ?  "Select Field" : "Class has no fields");
          chooser.setCopyJavadocVisible(false);
          chooser.show();
          Object[] selectedElements = chooser.getSelectedElements();
          if (selectedElements != null && selectedElements.length == 1) {
            PsiField field = (PsiField)selectedElements[0];
            myFieldChooser.setText(field.getName());
          }
        }
      }
    });
    myFieldChooser.setEnabled(false);
    return myPanel;
  }

  private void updateUI() {
    PsiClass selectedClass = getSelectedClass();
    myFieldChooser.setEnabled(selectedClass != null);
  }

  private PsiClass getSelectedClass() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    String classQName = myClassChooser.getText();
    if ("".equals(classQName)) {
      return null;
    }
    return psiManager.findClass(classQName, GlobalSearchScope.allScope(myProject));
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassChooser.getTextField();
  }

  public String getClassName() {
    return myClassChooser.getText();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
  }

  public String getFieldName() {
    return myFieldChooser.getText();
  }

  protected abstract boolean validateData();

  protected void doOKAction() {
    if(validateData()) {
      super.doOKAction();
    }
  }
}
