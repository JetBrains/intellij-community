/*
 * @author: Eugene Zhuravlev
 * Date: Sep 11, 2002
 * Time: 5:23:47 PM
 */
package com.intellij.debugger.ui;

import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ClassFilterEditorAddDialog extends DialogWrapper {
  private final Project myProject;
  private TextFieldWithBrowseButton myClassName;

  public ClassFilterEditorAddDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle("New Filter");
    init();
  }

  protected JComponent createCenterPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    myClassName = new TextFieldWithBrowseButton();
    _panel.add(new JLabel("Enter the filter pattern:"), BorderLayout.NORTH);
    _panel.add(myClassName, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalBox());


    JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(310, -1));

    JLabel iconLabel = new JLabel(Messages.getQuestionIcon());
    panel.add(iconLabel, BorderLayout.WEST);
    panel.add(box, BorderLayout.CENTER);

    myClassName.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PsiClass currentClass = getSelectedClass();
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser("Choose Class", GlobalSearchScope.allScope(myProject), null, null);
        if (currentClass != null) {
          PsiFile containingFile = currentClass.getContainingFile();
          if (containingFile != null) {
            PsiDirectory containingDirectory = containingFile.getContainingDirectory();
            if (containingDirectory != null) {
              chooser.selectDirectory(containingDirectory);
            }
          }
        }
        chooser.showDialog();
        PsiClass selectedClass = chooser.getSelectedClass();
        if (selectedClass != null) {
          myClassName.setText(selectedClass.getQualifiedName());
        }
      }
    });

    myClassName.setEnabled(myProject != null);

    return panel;
  }

  private PsiClass getSelectedClass() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    String classQName = myClassName.getText();
    if ("".equals(classQName)) {
      return null;
    }
    return psiManager.findClass(classQName, GlobalSearchScope.allScope(myProject));
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassName.getTextField();
  }

  public String getPattern() {
    return myClassName.getText();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
  }
}
