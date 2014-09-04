/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.wizard;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanStep extends StepAdapter{
  private JPanel myComponent;
  private TextFieldWithBrowseButton myTfWitgBtnChooseClass;
  private JRadioButton myRbBindToNewBean;
  private JRadioButton myRbBindToExistingBean;
  JTextField myTfShortClassName;
  private TextFieldWithBrowseButton myTfWithBtnChoosePackage;
  private JLabel myPackageLabel;
  private JLabel myExistClassLabel;
  private final WizardData myData;

  public BeanStep(@NotNull final WizardData data) {
    myData = data;

    myPackageLabel.setLabelFor(myTfWithBtnChoosePackage.getTextField());
    myExistClassLabel.setLabelFor(myTfWitgBtnChooseClass.getTextField());

    final ItemListener itemListener = new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        final boolean state = myRbBindToNewBean.isSelected();

        myTfShortClassName.setEnabled(state);
        myTfWithBtnChoosePackage.setEnabled(state);

        myTfWitgBtnChooseClass.setEnabled(!state);
      }
    };
    myRbBindToNewBean.addItemListener(itemListener);
    myRbBindToExistingBean.addItemListener(itemListener);

    {
      final ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(myRbBindToNewBean);
      buttonGroup.add(myRbBindToExistingBean);
    }

    myTfWitgBtnChooseClass.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myData.myProject).createWithInnerClassesScopeChooser(
            UIDesignerBundle.message("title.choose.bean.class"),
            GlobalSearchScope.projectScope(myData.myProject),
            new ClassFilter() {
              public boolean isAccepted(final PsiClass aClass) {
                return aClass.getParent() instanceof PsiJavaFile;
              }
            },
            null);
          chooser.showDialog();
          final PsiClass aClass = chooser.getSelected();
          if (aClass == null) {
            return;
          }
          final String fqName = aClass.getQualifiedName();
          myTfWitgBtnChooseClass.setText(fqName);
        }
      }
    );

    myTfWithBtnChoosePackage.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final PackageChooserDialog dialog = new PackageChooserDialog(UIDesignerBundle.message("title.choose.package"), myData.myProject);
        dialog.selectPackage(myTfWithBtnChoosePackage.getText());
        dialog.show();
        final PsiPackage aPackage = dialog.getSelectedPackage();
        if (aPackage != null) {
          myTfWithBtnChoosePackage.setText(aPackage.getQualifiedName());
        }
      }
    });
  }

  public void _init() {
    // Select way of binding
    if(myData.myBindToNewBean){
      myRbBindToNewBean.setSelected(true);
    }
    else{
      myRbBindToExistingBean.setSelected(true);
    }

    // New bean
    myTfShortClassName.setText(myData.myShortClassName);
    myTfWithBtnChoosePackage.setText(myData.myPackageName);

    // Existing bean
    myTfWitgBtnChooseClass.setText(
      myData.myBeanClass != null ? myData.myBeanClass.getQualifiedName() : null
    );
  }

  private void resetBindings(){
    for(int i = myData.myBindings.length - 1; i >= 0; i--){
      myData.myBindings[i].myBeanProperty = null;
    }
  }

  public void _commit(boolean finishChosen) throws CommitStepException{
    final boolean newBindToNewBean = myRbBindToNewBean.isSelected();
    if(myData.myBindToNewBean != newBindToNewBean){
      resetBindings();
    }

    myData.myBindToNewBean = newBindToNewBean;

    if(myData.myBindToNewBean){ // new bean
      final String oldShortClassName = myData.myShortClassName;
      final String oldPackageName = myData.myPackageName;

      final String shortClassName = myTfShortClassName.getText().trim();
      if(shortClassName.length() == 0){
        throw new CommitStepException(UIDesignerBundle.message("error.please.specify.class.name.of.the.bean.to.be.created"));
      }
      final PsiManager psiManager = PsiManager.getInstance(myData.myProject);
      if(!PsiNameHelper.getInstance(psiManager.getProject()).isIdentifier(shortClassName)){
        throw new CommitStepException(UIDesignerBundle.message("error.X.is.not.a.valid.class.name", shortClassName));
      }

      final String packageName = myTfWithBtnChoosePackage.getText().trim();
      if(packageName.length() != 0 && JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName) == null){
        throw new CommitStepException(UIDesignerBundle.message("error.package.with.name.X.does.not.exist", packageName));
      }

      myData.myShortClassName = shortClassName;
      myData.myPackageName = packageName;

      // check whether new class already exists
      {
        final String fullClassName = packageName.length() != 0 ? packageName + "." + shortClassName : shortClassName;
        final Module module = ModuleUtil.findModuleForFile(myData.myFormFile, myData.myProject);
        if (JavaPsiFacade.getInstance(psiManager.getProject())
          .findClass(fullClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null) {
          throw new CommitStepException(UIDesignerBundle.message("error.cannot.create.class.X.because.it.already.exists", fullClassName));
        }
      }

      if(
        !Comparing.equal(oldShortClassName, shortClassName) ||
        !Comparing.equal(oldPackageName, packageName)
      ){
        // After bean class changed we need to reset all previously set bindings
        resetBindings();
      }
    }
    else{ // existing bean
      final String oldFqClassName = myData.myBeanClass != null ? myData.myBeanClass.getQualifiedName() : null;
      final String newFqClassName = myTfWitgBtnChooseClass.getText().trim();
      if(newFqClassName.length() == 0){
        throw new CommitStepException(UIDesignerBundle.message("error.please.specify.fully.qualified.name.of.bean.class"));
      }
      final PsiClass aClass =
        JavaPsiFacade.getInstance(myData.myProject).findClass(newFqClassName, GlobalSearchScope.allScope(myData.myProject));
      if(aClass == null){
        throw new CommitStepException(UIDesignerBundle.message("error.class.with.name.X.does.not.exist", newFqClassName));
      }
      myData.myBeanClass = aClass;

      if(!Comparing.equal(oldFqClassName, newFqClassName)){
        // After bean class changed we need to reset all previously set bindings
        resetBindings();
      }
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }
}
