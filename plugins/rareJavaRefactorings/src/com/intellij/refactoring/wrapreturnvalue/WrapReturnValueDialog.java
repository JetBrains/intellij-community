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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

class WrapReturnValueDialog extends RefactoringDialog {

  private final PsiMethod sourceMethod;
  private final JTextField sourceMethodTextField;

  private final JRadioButton createNewClassButton;
  private final JTextField classNameField;
  private final PackageNameReferenceEditorCombo packageTextField;
  private final JPanel myNewClassPanel;

  private final ReferenceEditorComboWithBrowseButton existingClassField;
  private final JRadioButton useExistingClassButton;
  private final JComboBox<PsiField> myFieldsCombo;
  private final JPanel myExistingClassPanel;

  private final JPanel myWholePanel;

  private final JRadioButton myCreateInnerClassButton;
  private final JTextField myInnerClassNameTextField;
  private final JPanel myCreateInnerPanel;
  private final ComboboxWithBrowseButton myDestinationCb;
  private static final String RECENT_KEYS = "WrapReturnValue.RECENT_KEYS";

  WrapReturnValueDialog(PsiMethod sourceMethod) {
    super(sourceMethod.getProject(), true);
    this.sourceMethod = sourceMethod;
    {
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
            .createWithInnerClassesScopeChooser(RefactorJBundle.message("select.wrapper.class"), GlobalSearchScope.allScope(myProject),
                                                null, null);
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
      ((DestinationFolderComboBox)myDestinationCb).setData(myProject, sourceMethod.getContainingFile().getContainingDirectory(),
                                                           packageTextField.getChildComponent());
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myWholePanel = new JPanel();
      myWholePanel.setLayout(new GridLayoutManager(9, 1, new Insets(0, 0, 0, 0), -1, -1));
      createNewClassButton = new JRadioButton();
      this.$$$loadButtonText$$$(createNewClassButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.create.new.class"));
      myWholePanel.add(createNewClassButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
      useExistingClassButton = new JRadioButton();
      this.$$$loadButtonText$$$(useExistingClassButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.use.existing.class"));
      myWholePanel.add(useExistingClassButton, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                   null, null, null, 0, false));
      myNewClassPanel = new JPanel();
      myNewClassPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myNewClassPanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.new.class.name"));
      myNewClassPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
      classNameField = new JTextField();
      myNewClassPanel.add(classNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              new Dimension(150, -1), null, 0, false));
      final JLabel label2 = new JLabel();
      this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle",
                                                                       "wrap.return.value.new.class.package.name"));
      myNewClassPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
      myNewClassPanel.add(packageTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                null, null, null, 0, false));
      final JLabel label3 = new JLabel();
      this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/RefactoringBundle", "target.destination.folder"));
      myNewClassPanel.add(label3, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
      myNewClassPanel.add(myDestinationCb, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
      myExistingClassPanel = new JPanel();
      myExistingClassPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myExistingClassPanel, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
      final JLabel label4 = new JLabel();
      this.$$$loadLabelText$$$(label4,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.existing.class.name"));
      myExistingClassPanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                           null, 0, false));
      myExistingClassPanel.add(existingClassField,
                               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                   0, false));
      myFieldsCombo = new JComboBox();
      myExistingClassPanel.add(myFieldsCombo, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
      final JLabel label5 = new JLabel();
      this.$$$loadLabelText$$$(label5,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.wrapper.field"));
      myExistingClassPanel.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                           null, 0, false));
      sourceMethodTextField = new JTextField();
      myWholePanel.add(sourceMethodTextField, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                  GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, new Dimension(400, -1), null, 0, false));
      final JLabel label6 = new JLabel();
      this.$$$loadLabelText$$$(label6,
                               this.$$$getMessageFromBundle$$$("messages/JavaRareRefactoringsBundle", "method.to.wrap.returns.from.label"));
      myWholePanel.add(label6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
      myCreateInnerClassButton = new JRadioButton();
      this.$$$loadButtonText$$$(myCreateInnerClassButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.create.inner.class"));
      myWholePanel.add(myCreateInnerClassButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
      myCreateInnerPanel = new JPanel();
      myCreateInnerPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 20, 0, 0), -1, -1));
      myWholePanel.add(myCreateInnerPanel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
      final JLabel label7 = new JLabel();
      this.$$$loadLabelText$$$(label7,
                               this.$$$getMessageFromBundle$$$("messages/JavaRefactoringBundle", "wrap.return.value.inner.class.name"));
      myCreateInnerPanel.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
      myInnerClassNameTextField = new JTextField();
      myCreateInnerPanel.add(myInnerClassNameTextField,
                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                 new Dimension(150, -1), null, 0, false));
      final Spacer spacer1 = new Spacer();
      myWholePanel.add(spacer1, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      label1.setLabelFor(classNameField);
      label3.setLabelFor(myDestinationCb);
      label4.setLabelFor(myFieldsCombo);
      label5.setLabelFor(myFieldsCombo);
      label7.setLabelFor(myInnerClassNameTextField);
    }
    setTitle(JavaRareRefactoringsBundle.message("wrap.return.value.title"));
    init();
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myWholePanel; }

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
      new WrapReturnValueProcessor(className, packageName, ((DestinationFolderComboBox)myDestinationCb).selectDirectory(
        new PackageWrapper(sourceMethod.getManager(), packageName), false),
                                   sourceMethod, useExistingClass, createInnerClass, (PsiField)myFieldsCombo.getSelectedItem()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final Project project = sourceMethod.getProject();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    if (myCreateInnerClassButton.isSelected()) {
      final String innerClassName = getInnerClassName().trim();
      if (!nameHelper.isIdentifier(innerClassName)) {
        throw new ConfigurationException(
          JavaRareRefactoringsBundle.message("dialog.message.invalid.inner.class.name", innerClassName));
      }
      final PsiClass containingClass = sourceMethod.getContainingClass();
      if (containingClass != null && containingClass.findInnerClassByName(innerClassName, false) != null) {
        throw new ConfigurationException(
          JavaRareRefactoringsBundle.message("dialog.message.inner.class.with.name.already.exist", innerClassName));
      }
    }
    else if (useExistingClassButton.isSelected()) {
      final String className = existingClassField.getText().trim();
      if (className.isEmpty() || !nameHelper.isQualifiedName(className)) {
        throw new ConfigurationException(
          JavaRareRefactoringsBundle.message("dialog.message.invalid.qualified.wrapper.class.name", className));
      }
      final Object item = myFieldsCombo.getSelectedItem();
      if (item == null) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.wrapper.field.not.found"));
      }
    }
    else {
      final String className = getClassName();
      if (className.isEmpty() || !nameHelper.isIdentifier(className)) {
        throw new ConfigurationException(JavaRareRefactoringsBundle.message("dialog.message.invalid.wrapper.class.name", className));
      }
      final String packageName = getPackageName();

      if (packageName.isEmpty() || !nameHelper.isQualifiedName(packageName)) {
        throw new ConfigurationException(
          JavaRareRefactoringsBundle.message("dialog.message.invalid.wrapper.class.package.name", packageName));
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
}