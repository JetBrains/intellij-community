package com.intellij.uiDesigner.wizard;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.FormEditingUtil;

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
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.BeanStep");

  private JPanel myComponent;
  private TextFieldWithBrowseButton myTfWitgBtnChooseClass;
  private JRadioButton myRbBindToNewBean;
  private JRadioButton myRbBindToExistingBean;
  JTextField myTfShortClassName;
  private TextFieldWithBrowseButton myTfWithBtnChoosePackage;
  private final WizardData myData;

  public BeanStep(final WizardData data) {
    LOG.assertTrue(data != null);

    myData = data;

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
            "Choose Bean Class",
            GlobalSearchScope.projectScope(myData.myProject),
            new TreeClassChooser.ClassFilter() {
              public boolean isAccepted(final PsiClass aClass) {
                return aClass.getParent() instanceof PsiJavaFile;
              }
            },
            null);
          chooser.showDialog();
          final PsiClass aClass = chooser.getSelectedClass();
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
        final PackageChooserDialog dialog = new PackageChooserDialog("Choose Package", myData.myProject);
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
        throw new CommitStepException("Please specify class name of the bean to be created");
      }
      final PsiManager psiManager = PsiManager.getInstance(myData.myProject);
      if(!psiManager.getNameHelper().isIdentifier(shortClassName)){
        throw new CommitStepException("\"" + shortClassName + "\" is not a valid class name");
      }

      final String packageName = myTfWithBtnChoosePackage.getText().trim();
      if(packageName.length() != 0 && psiManager.findPackage(packageName) == null){
        throw new CommitStepException("Package with name \"" + packageName + "\" does not exist");
      }

      myData.myShortClassName = shortClassName;
      myData.myPackageName = packageName;

      // check whether new class already exists
      {
        final String fullClassName = packageName.length() != 0 ? packageName + "." + shortClassName : shortClassName;
        final Module module = ModuleUtil.getModuleForFile(myData.myProject, myData.myFormFile);
        if (psiManager.findClass(fullClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null) {
          throw new CommitStepException("Cannot create class \"" + fullClassName + "\" because it already exists");
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
        throw new CommitStepException("Please specify fully qualified name of bean class");
      }
      final PsiClass aClass = PsiManager.getInstance(myData.myProject).findClass(
        newFqClassName,
        GlobalSearchScope.allScope(myData.myProject)
      );
      if(aClass == null){
        throw new CommitStepException("Class with name \"" + newFqClassName + "\" does not exist");
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

  public Icon getIcon() {
    return IconLoader.getIcon("/com/intellij/uiDesigner/icons/dataBinding.png");
  }
}
