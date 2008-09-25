package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.PackageNameUtil;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class WrapReturnValueDialog extends RefactoringDialog {

  private PsiMethod sourceMethod;
  private JTextField sourceMethodTextField;

  private JRadioButton createNewClassButton;
  private JTextField classNameField;
  private TextFieldWithBrowseButton packageTextField;
  private JPanel myNewClassPanel;

  private TextFieldWithBrowseButton existingClassField;
  private JRadioButton useExistingClassButton;
  private JComboBox myFieldsCombo;
  private JPanel myExistingClassPanel;

  private JPanel myWholePanel;

  private JRadioButton myCreateInnerClassButton;
  private JTextField myInnerClassNameTextField;
  private JPanel myCreateInnerPanel;

  WrapReturnValueDialog(PsiMethod sourceMethod) {
    super(sourceMethod.getProject(), true);
    this.sourceMethod = sourceMethod;
    setTitle(RefactorJBundle.message("wrap.return.value.title"));
    init();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.WrapReturnValue";
  }

  protected void doAction() {
    final boolean useExistingClass = useExistingClassButton.isSelected();
    final boolean createInnerClass = myCreateInnerClassButton.isSelected();
    final String existingClassName = existingClassField.getText().trim();
    final String className;
    final String packageName;
    if (useExistingClass) {
      className = StringUtil.getShortName(existingClassName);
      packageName = StringUtil.getPackageName(existingClassName);
    }
    else if (createInnerClass) {
      className = getInnerClassName();
      packageName = "";
    }
    else {
      className = getClassName();
      packageName = getPackageName();
    }
    invokeRefactoring(
      new WrapReturnValueProcessor(className, packageName, sourceMethod, useExistingClass, createInnerClass, (PsiField)myFieldsCombo.getSelectedItem()));
  }

  protected boolean areButtonsValid() {
    if (useExistingClassButton.isSelected()) {
      return myFieldsCombo.getSelectedItem() != null && existingClassField.getText().length() != 0;
    }
    if (myCreateInnerClassButton.isSelected()) {
      final String innerClassName = getInnerClassName();
      return innerClassName.length() > 0 && sourceMethod.getContainingClass().findInnerClassByName(innerClassName, false) == null;
    }
    final Project project = sourceMethod.getProject();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
    final String packageName = getPackageName();
    if (packageName.length() == 0 || PackageNameUtil.containsNonIdentifier(nameHelper, packageName)) {
      return false;
    }
    final String className = getClassName();
    return !(className.length() == 0 || !nameHelper.isIdentifier(className));
  }

  private String getInnerClassName() {
    return myInnerClassNameTextField.getText().trim();
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  protected JComponent createCenterPanel() {
    sourceMethodTextField.setEditable(false);

    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };
    existingClassField.getTextField().getDocument().addDocumentListener(docListener);
    packageTextField.getTextField().getDocument().addDocumentListener(docListener);
    classNameField.getDocument().addDocumentListener(docListener);
    myFieldsCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateButtons();
      }
    });
    myInnerClassNameTextField.getDocument().addDocumentListener(docListener);

    final PsiFile file = sourceMethod.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile)file).getPackageName();
      packageTextField.setText(packageName);
    }

    final PsiClass containingClass = sourceMethod.getContainingClass();
    final String containingClassName = containingClass instanceof PsiAnonymousClass ? "Anonymous " + ((PsiAnonymousClass)containingClass).getBaseClassType().getClassName() : containingClass.getName();
    final String sourceMethodName = sourceMethod.getName();
    sourceMethodTextField.setText(containingClassName + '.' + sourceMethodName);
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    buttonGroup.add(myCreateInnerClassButton);
    createNewClassButton.setSelected(true);
    final ActionListener enableListener = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        toggleRadioEnablement();
      }
    };
    useExistingClassButton.addActionListener(enableListener);
    createNewClassButton.addActionListener(enableListener);
    myCreateInnerClassButton.addActionListener(enableListener);
    toggleRadioEnablement();
    
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myFieldsCombo.setModel(model);
    myFieldsCombo.setRenderer(new DefaultPsiElementCellRenderer());
    existingClassField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = sourceMethod.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final TreeClassChooserDialog chooser =
          new TreeClassChooserDialog(RefactorJBundle.message("select.wrapper.class"), project, scope, null, null);
        final String classText = existingClassField.getText();
        final PsiClass currentClass =
          JavaPsiFacade.getInstance(PsiManager.getInstance(project).getProject()).findClass(classText, GlobalSearchScope.allScope(project));
        if (currentClass != null) {
          chooser.selectClass(currentClass);
        }
        chooser.show();
        final PsiClass selectedClass = chooser.getSelectedClass();
        if (selectedClass != null) {
          final String className = selectedClass.getQualifiedName();
          existingClassField.setText(className);
          model.removeAllElements();
          for (PsiField field : selectedClass.getFields()) {
            if (TypeConversionUtil.isAssignable(field.getType(), sourceMethod.getReturnType())) {
              model.addElement(field);
            }
          }
        }
      }
    });

    packageTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = sourceMethod.getProject();
        final PackageChooserDialog chooser = new PackageChooserDialog(RefactorJBundle.message("choose.destination.package.label"), project);
        final String packageText = packageTextField.getText();
        chooser.selectPackage(packageText);
        chooser.show();
        final PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          final String packageName = aPackage.getQualifiedName();
          packageTextField.setText(packageName);
        }
      }
    });
    return myWholePanel;
  }

  private void toggleRadioEnablement() {
    UIUtil.setEnabled(myExistingClassPanel, useExistingClassButton.isSelected(), true);
    UIUtil.setEnabled(myNewClassPanel, createNewClassButton.isSelected(), true);
    UIUtil.setEnabled(myCreateInnerPanel, myCreateInnerClassButton.isSelected(), true);
    validateButtons();
  }


  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.WrapReturnValue);
  }
}