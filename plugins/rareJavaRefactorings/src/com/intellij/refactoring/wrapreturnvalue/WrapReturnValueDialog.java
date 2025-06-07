// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class WrapReturnValueDialog extends RefactoringDialog {

  private final PsiMethod sourceMethod;
  private JTextField sourceMethodTextField;

  private JRadioButton createNewClassButton;
  private JTextField classNameField;
  private PackageNameReferenceEditorCombo packageTextField;
  private JPanel myNewClassPanel;

  private ReferenceEditorComboWithBrowseButton existingClassField;
  private JRadioButton useExistingClassButton;
  private JComboBox<PsiField> myFieldsCombo;
  private JPanel myExistingClassPanel;

  private JPanel myWholePanel;

  private JRadioButton myCreateInnerClassButton;
  private JTextField myInnerClassNameTextField;
  private JPanel myCreateInnerPanel;
  private ComboboxWithBrowseButton myDestinationCb;
  private static final String RECENT_KEYS = "WrapReturnValue.RECENT_KEYS";

  WrapReturnValueDialog(PsiMethod sourceMethod) {
    super(sourceMethod.getProject(), true);
    this.sourceMethod = sourceMethod;
    setTitle(JavaRareRefactoringsBundle.message("wrap.return.value.title"));
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "RefactorJ.WrapReturnValue";
  }

  @Override
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
      new WrapReturnValueProcessor(className, packageName, ((DestinationFolderComboBox)myDestinationCb).selectDirectory(new PackageWrapper(sourceMethod.getManager(), packageName), false),
                                   sourceMethod, useExistingClass, createInnerClass, (PsiField)myFieldsCombo.getSelectedItem()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final Project project = sourceMethod.getProject();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    if (myCreateInnerClassButton.isSelected()) {
      final String innerClassName = getInnerClassName().trim();
      if (!nameHelper.isIdentifier(innerClassName)) throw new ConfigurationException(
        JavaRareRefactoringsBundle.message("dialog.message.invalid.inner.class.name", innerClassName));
      final PsiClass containingClass = sourceMethod.getContainingClass();
      if (containingClass != null && containingClass.findInnerClassByName(innerClassName, false) != null) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.inner.class.with.name.already.exist", innerClassName));
      }
    } else if (useExistingClassButton.isSelected()) {
      final String className = existingClassField.getText().trim();
      if (className.isEmpty() || !nameHelper.isQualifiedName(className)) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.invalid.qualified.wrapper.class.name", className));
      }
      final Object item = myFieldsCombo.getSelectedItem();
      if (item == null) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.wrapper.field.not.found"));
      }
    } else {
      final String className = getClassName();
      if (className.isEmpty() || !nameHelper.isIdentifier(className)) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.invalid.wrapper.class.name", className));
      }
      final String packageName = getPackageName();

      if (packageName.isEmpty() || !nameHelper.isQualifiedName(packageName)) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.invalid.wrapper.class.package.name", packageName));
      }
    }
  }

  private @NotNull String getInnerClassName() {
    return myInnerClassNameTextField.getText().trim();
  }

  public @NotNull String getPackageName() {
    return packageTextField.getText().trim();
  }

  public @NotNull String getClassName() {
    return classNameField.getText().trim();
  }

  @Override
  protected JComponent createCenterPanel() {
    sourceMethodTextField.setEditable(false);

    final DocumentListener docListener = new DocumentAdapter() {
      @Override
      protected void textChanged(final @NotNull DocumentEvent e) {
        validateButtons();
      }
    };

    classNameField.getDocument().addDocumentListener(docListener);
    myFieldsCombo.addActionListener(new ActionListener() {
      @Override
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
    assert containingClass != null : sourceMethod;
    final String containingClassName = containingClass instanceof PsiAnonymousClass
                                       ? JavaBundle.message("wrap.return.value.anonymous.class.presentation",
                                                            ((PsiAnonymousClass)containingClass).getBaseClassType().getClassName())
                                       : containingClass.getName();
    final String sourceMethodName = sourceMethod.getName();
    sourceMethodTextField.setText(containingClassName + '.' + sourceMethodName);
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    buttonGroup.add(myCreateInnerClassButton);
    createNewClassButton.setSelected(true);
    final ActionListener enableListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        toggleRadioEnablement();
      }
    };
    useExistingClassButton.addActionListener(enableListener);
    createNewClassButton.addActionListener(enableListener);
    myCreateInnerClassButton.addActionListener(enableListener);
    toggleRadioEnablement();

    final DefaultComboBoxModel<PsiField> model = new DefaultComboBoxModel<>();
    myFieldsCombo.setModel(model);
    myFieldsCombo.setRenderer(SimpleListCellRenderer.create((label, field, index) -> {
      if (field != null) {
        label.setText(field.getName());
        label.setIcon(field.getIcon(Iconable.ICON_FLAG_VISIBILITY));
      }
    }));
    existingClassField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
        final PsiClass currentClass = facade.findClass(existingClassField.getText(), GlobalSearchScope.allScope(myProject));
        if (currentClass != null) {
          model.removeAllElements();
          final PsiType returnType = sourceMethod.getReturnType();
          assert returnType != null;
          for (PsiField field : currentClass.getFields()) {
            final PsiType fieldType = field.getType();
            if (TypeConversionUtil.isAssignable(fieldType, returnType)) {
              model.addElement(field);
            }
            else {
              if (WrapReturnValueProcessor.getInferredType(fieldType, returnType, currentClass, sourceMethod) != null) {
                model.addElement(field);
              }
            }
          }
        }
      }
    });
    return myWholePanel;
  }

  private void toggleRadioEnablement() {
    UIUtil.setEnabled(myExistingClassPanel, useExistingClassButton.isSelected(), true);
    UIUtil.setEnabled(myNewClassPanel, createNewClassButton.isSelected(), true);
    UIUtil.setEnabled(myCreateInnerPanel, myCreateInnerClassButton.isSelected(), true);
    final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    if (useExistingClassButton.isSelected()) {
      focusManager.requestFocus(existingClassField, true);
    }
    else if (myCreateInnerClassButton.isSelected()) {
      focusManager.requestFocus(myInnerClassNameTextField, true);
    }
    else {
      focusManager.requestFocus(classNameField, true);
    }
    validateButtons();
  }


  @Override
  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  @Override
  protected String getHelpId() {
    return HelpID.WrapReturnValue;
  }

  private void createUIComponents() {
    final com.intellij.openapi.editor.event.DocumentListener adapter = new com.intellij.openapi.editor.event.DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
      }
    };

    packageTextField =
      new PackageNameReferenceEditorCombo("", myProject, RECENT_KEYS, RefactoringBundle.message("choose.destination.package"));
    packageTextField.getChildComponent().getDocument().addDocumentListener(adapter);

    existingClassField = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject())
          .createWithInnerClassesScopeChooser(RefactorJBundle.message("select.wrapper.class"), GlobalSearchScope.allScope(myProject), null, null);
        final String classText = existingClassField.getText();
        final PsiClass currentClass = JavaPsiFacade.getInstance(myProject).findClass(classText, GlobalSearchScope.allScope(myProject));
        if (currentClass != null) {
          chooser.select(currentClass);
        }
        chooser.showDialog();
        final PsiClass selectedClass = chooser.getSelected();
        if (selectedClass != null) {
          existingClassField.setText(selectedClass.getQualifiedName());
        }
      }
    }, "", myProject, true, RECENT_KEYS);
    existingClassField.getChildComponent().getDocument().addDocumentListener(adapter);

    myDestinationCb = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return getPackageName();
      }
    };
    ((DestinationFolderComboBox)myDestinationCb).setData(myProject, sourceMethod.getContainingFile().getContainingDirectory(), packageTextField.getChildComponent());
  }
}