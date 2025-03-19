// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.palette;

import com.intellij.CommonBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.ImageFileFilter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public final class ComponentItemDialog extends DialogWrapper {
  private JPanel myPanel;
  private final ComponentWithBrowseButton<EditorTextField> myTfClassName;
  private final Project myProject;
  private final ComponentItem myItemToBeEdited;
  private final boolean myOneOff;
  private JLabel myLblIcon;
  private TextFieldWithBrowseButton myTfIconPath;
  private JCheckBox myChkHorCanShrink;
  private JCheckBox myChkHorCanGrow;
  private JCheckBox myChkHorWantGrow;
  private JCheckBox myChkVerCanShrink;
  private JCheckBox myChkVerCanGrow;
  private JCheckBox myChkVerWantGrow;
  private JPanel myClassNamePlaceholder;
  private JRadioButton myClassRadioButton;
  private JRadioButton myNestedFormRadioButton;
  private TextFieldWithBrowseButton myTfNestedForm;
  private JCheckBox myAutoCreateBindingCheckbox;
  private JCheckBox myCanAttachLabelCheckbox;
  private JPanel myHSizePolicyPanel;
  private JPanel myVSizePolicyPanel;
  private JComboBox myGroupComboBox;
  private JLabel myGroupLabel;
  private JCheckBox myIsContainerCheckBox;
  private JLabel myErrorLabel;
  private final EditorTextField myEditorTextField;
  private Document myDocument;

  /**
   * @param itemToBeEdited item to be edited. If user closes dialog by "OK" button then
   */
  public ComponentItemDialog(final Project project, final Component parent, @NotNull ComponentItem itemToBeEdited, final boolean oneOff) {
    super(parent, false);
    myProject = project;

    myItemToBeEdited = itemToBeEdited;
    myOneOff = oneOff;

    myEditorTextField = new EditorTextField("", project, JavaFileType.INSTANCE);
    myEditorTextField.setFontInheritedFromLAF(true);
    myTfClassName = new ComponentWithBrowseButton<>(myEditorTextField, new MyChooseClassActionListener(project));

    PsiFile boundForm = itemToBeEdited.getBoundForm();
    if (boundForm != null) {
      myNestedFormRadioButton.setSelected(true);
      myTfNestedForm.setText(FormEditingUtil.buildResourceName(boundForm));
    }
    else {
      myClassRadioButton.setSelected(true);
      setEditorText(myItemToBeEdited.getClassName().replace('$', '.'));
    }
    updateEnabledTextField();

    myTfClassName.getChildComponent().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
        updateOKAction();
      }
    });

    myTfClassName.getButton().setEnabled(!project.isDefault()); // chooser should not work in default project
    myClassNamePlaceholder.setLayout(new BorderLayout());
    myClassNamePlaceholder.add(myTfClassName, BorderLayout.CENTER);

    myTfIconPath.setText(myItemToBeEdited.getIconPath());
    myTfIconPath.addActionListener(new MyChooseFileActionListener(project, new ImageFileFilter(null), myTfIconPath,
                                                                  UIDesignerBundle.message("add.component.choose.icon")));

    myTfNestedForm.addActionListener(new MyChooseFileActionListener(project, new TreeFileChooser.PsiFileFilter() {
      @Override
      public boolean accept(PsiFile file) {
        return file.getFileType().equals(GuiFormFileType.INSTANCE);
      }
    }, myTfNestedForm, UIDesignerBundle.message("add.component.choose.form")));

    myTfNestedForm.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateOKAction();
      }
    });

    final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();

    // Horizontal size policy
    {
      final int hSizePolicy = defaultConstraints.getHSizePolicy();
      myChkHorCanShrink.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
      myChkHorCanGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
      myChkHorWantGrow.setSelected((hSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }

    // Vertical size policy
    {
      final int vSizePolicy = defaultConstraints.getVSizePolicy();
      myChkVerCanShrink.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0);
      myChkVerCanGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
      myChkVerWantGrow.setSelected((vSizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }

    myIsContainerCheckBox.setSelected(itemToBeEdited.isContainer());
    myAutoCreateBindingCheckbox.setSelected(itemToBeEdited.isAutoCreateBinding());
    myCanAttachLabelCheckbox.setSelected(itemToBeEdited.isCanAttachLabel());

    myLblIcon.setLabelFor(myTfIconPath);
    myClassRadioButton.addChangeListener(new MyRadioChangeListener());
    myNestedFormRadioButton.addChangeListener(new MyRadioChangeListener());

    if (oneOff) {
      myLblIcon.setVisible(false);
      myTfIconPath.setVisible(false);
      myCanAttachLabelCheckbox.setVisible(false);
      myHSizePolicyPanel.setVisible(false);
      myVSizePolicyPanel.setVisible(false);
    }

    updateOKAction();

    init();
  }

  void showGroupChooser(GroupItem defaultGroup) {
    myGroupLabel.setVisible(true);
    myGroupComboBox.setVisible(true);
    List<GroupItem> groups = Palette.getInstance(myProject).getGroups();
    myGroupComboBox.setModel(new DefaultComboBoxModel(groups.toArray()));
    myGroupComboBox.setSelectedItem(defaultGroup);
    myGroupComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateOKAction();
      }
    });
    updateOKAction();
  }

  GroupItem getSelectedGroup() {
    return (GroupItem) myGroupComboBox.getSelectedItem();
  }

  private void setEditorText(final String className) {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment(className, defaultPackage, true, true);
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(fragment);
    myEditorTextField.setDocument(myDocument);
    updateOKAction();
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.addEditPaletteComponent";
  }

  @Override
  protected void doOKAction() {
    // TODO[vova] implement validation
    if (myClassRadioButton.isSelected()) {
      final String className = myDocument.getText().trim();
      PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      if (psiClass != null) {
        myItemToBeEdited.setClassName(getClassOrInnerName(psiClass));
      }
      else {
        myItemToBeEdited.setClassName(className);
      }
    }
    else {
      if (!saveNestedForm()) return;
    }
    myItemToBeEdited.setIconPath(myTfIconPath.getText().trim());

    {
      final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
      // Horizontal size policy
      defaultConstraints.setHSizePolicy(
        (myChkHorCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkHorCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkHorWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );

      // Vertical size policy
      defaultConstraints.setVSizePolicy(
        (myChkVerCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkVerCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkVerWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );

      defaultConstraints.setFill(
        (myChkHorWantGrow.isSelected() ? GridConstraints.FILL_HORIZONTAL : 0) |
        (myChkVerWantGrow.isSelected() ? GridConstraints.FILL_VERTICAL : 0)
      );
    }

    myItemToBeEdited.setIsContainer(myIsContainerCheckBox.isSelected());
    myItemToBeEdited.setAutoCreateBinding(myAutoCreateBindingCheckbox.isSelected());
    myItemToBeEdited.setCanAttachLabel(myCanAttachLabelCheckbox.isSelected());

    super.doOKAction();
  }

  private boolean saveNestedForm() {
    VirtualFile formFile = ResourceFileUtil.findResourceFileInProject(myProject, myTfNestedForm.getText());
    if (formFile == null) {
      Messages.showErrorDialog(getWindow(), UIDesignerBundle.message("add.component.cannot.load.form", myTfNestedForm.getText()), CommonBundle.getErrorTitle());
      return false;
    }
    LwRootContainer lwRootContainer;
    try {
      lwRootContainer = Utils.getRootContainer(formFile.getInputStream(), null);
    }
    catch (Exception e) {
      Messages.showErrorDialog(getWindow(), e.getMessage(), CommonBundle.getErrorTitle());
      return false;
    }
    if (lwRootContainer.getClassToBind() == null) {
      Messages.showErrorDialog(getWindow(), UIDesignerBundle.message("add.component.form.not.bound"), CommonBundle.getErrorTitle());
      return false;
    }
    if (lwRootContainer.getComponent(0).getBinding() == null) {
      Messages.showErrorDialog(getWindow(), UIDesignerBundle.message("add.component.root.not.bound"),
                               CommonBundle.getErrorTitle());
      return false;
    }
    PsiClass psiClass =
      JavaPsiFacade.getInstance(myProject).findClass(lwRootContainer.getClassToBind(), GlobalSearchScope.projectScope(myProject));
    if (psiClass != null) {
      myItemToBeEdited.setClassName(getClassOrInnerName(psiClass));
    }
    else {
      myItemToBeEdited.setClassName(lwRootContainer.getClassToBind());
    }
    return true;
  }

  @Override
  protected String getDimensionServiceKey() {
    if (myOneOff) {
      return "#com.intellij.uiDesigner.palette.ComponentItemDialog.OneOff";
    }
    return "#com.intellij.uiDesigner.palette.ComponentItemDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTfClassName.getChildComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void updateEnabledTextField() {
    myEditorTextField.setEnabled(myClassRadioButton.isSelected());
    myTfNestedForm.setEnabled(myNestedFormRadioButton.isSelected());
    updateOKAction();
  }

  private void updateOKAction() {
    setOKActionEnabled(isOKEnabled());
  }

  private boolean isOKEnabled() {
    myErrorLabel.setText(" ");
    if (myClassRadioButton.isSelected()) {
      if (myDocument == null) {  // why?
        return false;
      }
      if (!PsiNameHelper.getInstance(myProject).isQualifiedName(myDocument.getText())) {
        if (myDocument.getTextLength() > 0) {
          myErrorLabel.setText(UIDesignerBundle.message("add.component.error.qualified.name.required"));
        }
        return false;
      }
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-766233")) {
        final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myProject);
        PsiClass psiClass = javaPsiFacade.findClass(myDocument.getText(), ProjectScope.getAllScope(myProject));
        PsiClass componentClass = javaPsiFacade.findClass(JComponent.class.getName(), ProjectScope.getAllScope(myProject));
        if (psiClass != null && componentClass != null && !InheritanceUtil.isInheritorOrSelf(psiClass, componentClass, true)) {
          myErrorLabel.setText(UIDesignerBundle.message("add.component.error.component.required"));
          return false;
        }
      }
    }
    else {
      if (myTfNestedForm.getText().isEmpty()) {
        return false;
      }
    }
    if (myGroupComboBox.isVisible() && myGroupComboBox.getSelectedItem() == null) {
      return false;
    }
    return true;
  }

  private static String getClassOrInnerName(final PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if(parentClass != null) {
      return getClassOrInnerName(parentClass) + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  private class MyChooseClassActionListener implements ActionListener {
    private final Project myProject;

    MyChooseClassActionListener(final Project project) {
      myProject = project;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-766230")) {
        final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
        final TreeClassChooser chooser = factory.createInheritanceClassChooser(UIDesignerBundle.message("title.choose.component.class"),
                                                                               GlobalSearchScope.allScope(myProject),
                                                                               JavaPsiFacade.getInstance(myProject)
                                                                                 .findClass(JComponent.class.getName(),
                                                                                            GlobalSearchScope.allScope(myProject)), true,
                                                                               true, null);
        chooser.showDialog();
        final PsiClass result = chooser.getSelected();
        if (result != null) {
          setEditorText(result.getQualifiedName());
        }
      }
    }
  }

  private static class MyChooseFileActionListener implements ActionListener {
    private final Project myProject;
    private final TreeFileChooser.PsiFileFilter myFilter;
    private final TextFieldWithBrowseButton myTextField;
    private final @Nls String myTitle;

    MyChooseFileActionListener(final Project project,
                                      final TreeFileChooser.PsiFileFilter filter,
                                      final TextFieldWithBrowseButton textField,
                                      final @Nls String title) {
      myProject = project;
      myFilter = filter;
      myTextField = textField;
      myTitle = title;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      PsiFile formFile = null;
      if (!myTextField.getText().isEmpty()) {
        VirtualFile formVFile = ResourceFileUtil.findResourceFileInScope(myTextField.getText(), myProject, ProjectScope.getAllScope(myProject));
        if (formVFile != null) {
          formFile = PsiManager.getInstance(myProject).findFile(formVFile);
        }
      }
      final TreeFileChooserFactory factory = TreeFileChooserFactory.getInstance(myProject);
      TreeFileChooser fileChooser = factory.createFileChooser(myTitle, formFile, null, myFilter, true, true);
      fileChooser.showDialog();
      PsiFile file = fileChooser.getSelectedFile();
      if (file != null) {
        myTextField.setText(FormEditingUtil.buildResourceName(file));
      }
    }
  }

  private class MyRadioChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      updateEnabledTextField();
    }
  }
}
