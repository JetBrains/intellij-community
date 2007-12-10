/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author: Eugene Zhuravlev
 * Date: Sep 11, 2002
 * Time: 5:23:47 PM
 */
package com.intellij.ui.classFilter;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.UIBundle;

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
    setTitle(UIBundle.message("class.filter.editor.add.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    Box box = Box.createVerticalBox();

    JPanel _panel = new JPanel(new BorderLayout());
    myClassName = new TextFieldWithBrowseButton();
    _panel.add(new JLabel(UIBundle.message("label.class.filter.editor.add.dialog.filter.pattern")), BorderLayout.NORTH);
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
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
          UIBundle.message("class.filter.editor.choose.class.title"), GlobalSearchScope.allScope(myProject), null, null);
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
    return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(classQName, GlobalSearchScope.allScope(myProject));
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
