package com.intellij.uiDesigner.palette;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.ImageFileFilter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Vladimir Kondratyev
 */
public final class ComponentItemDialog extends DialogWrapper{
  private JPanel myPanel;
  private ComponentWithBrowseButton<EditorTextField> myTfClassName;
  private Project myProject;
  private final ComponentItem myItemToBeEdited;
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
  private EditorTextField myEditorTextField;
  private Document myDocument;

  /**
   * @param itemToBeEdited item to be edited. If user closes dialog by "OK" button then
   */
  public ComponentItemDialog(final Project project, final Component parent, @NotNull ComponentItem itemToBeEdited) {
    super(parent, false);
    myProject = project;

    myItemToBeEdited = itemToBeEdited;

    myEditorTextField = new EditorTextField("", project, StdFileTypes.JAVA);
    myEditorTextField.setFontInheritedFromLAF(true);
    myTfClassName = new ComponentWithBrowseButton<EditorTextField>(myEditorTextField, new MyChooseClassActionListener(project));

    PsiFile boundForm = itemToBeEdited.getBoundForm();
    if (boundForm != null) {
      myNestedFormRadioButton.setSelected(true);
      myTfNestedForm.setText(GuiEditorUtil.buildResourceName(boundForm));
    }
    else {
      myClassRadioButton.setSelected(true);
      setEditorText(myItemToBeEdited.getClassName().replace('$', '.'));
    }
    updateEnabledTextField();

    myTfClassName.getButton().setEnabled(!project.isDefault()); // chooser should not work in default project
    myClassNamePlaceholder.setLayout(new BorderLayout());
    myClassNamePlaceholder.add(myTfClassName, BorderLayout.CENTER);

    myTfIconPath.setText(myItemToBeEdited.getIconPath());
    myTfIconPath.addActionListener(new MyChooseFileActionListener(project, new ImageFileFilter(null), myTfIconPath));

    myTfNestedForm.addActionListener(new MyChooseFileActionListener(project, new TreeFileChooser.PsiFileFilter() {
      public boolean accept(PsiFile file) {
        return file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM);
      }
    }, myTfNestedForm));


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

    myLblIcon.setLabelFor(myTfIconPath);
    myClassRadioButton.addChangeListener(new MyRadioChangeListener());
    myNestedFormRadioButton.addChangeListener(new MyRadioChangeListener());

    init();
  }

  private void setEditorText(final String className) {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiElementFactory factory = manager.getElementFactory();
    PsiPackage defaultPackage = manager.findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment(className, defaultPackage, true, true);
    myDocument = PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
    myEditorTextField.setDocument(myDocument);
  }


  protected void doOKAction() {
    // TODO[vova] implement validation
    if (myClassRadioButton.isSelected()) {
      final String className = myDocument.getText().trim();
      PsiClass psiClass = PsiManager.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
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

    // Horizontal size policy
    {
      final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
      defaultConstraints.setHSizePolicy(
        (myChkHorCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkHorCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkHorWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );
    }

    // Vertical size policy
    {
      final GridConstraints defaultConstraints = myItemToBeEdited.getDefaultConstraints();
      defaultConstraints.setVSizePolicy(
        (myChkVerCanShrink.isSelected() ? GridConstraints.SIZEPOLICY_CAN_SHRINK : 0) |
        (myChkVerCanGrow.isSelected() ? GridConstraints.SIZEPOLICY_CAN_GROW : 0) |
        (myChkVerWantGrow.isSelected() ? GridConstraints.SIZEPOLICY_WANT_GROW : 0)
      );
    }

    super.doOKAction();
  }

  private boolean saveNestedForm() {
    VirtualFile formFile = ModuleUtil.findResourceFileInProject(myProject, myTfNestedForm.getText());
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
    PsiClass psiClass = PsiManager.getInstance(myProject).findClass(lwRootContainer.getClassToBind(),
                                                                    GlobalSearchScope.projectScope(myProject));
    if (psiClass != null) {
      myItemToBeEdited.setClassName(getClassOrInnerName(psiClass));
    }
    else {
      myItemToBeEdited.setClassName(lwRootContainer.getClassToBind());
    }
    return true;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.uiDesigner.palette.ComponentItemDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfClassName.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void updateEnabledTextField() {
    myEditorTextField.setEnabled(myClassRadioButton.isSelected());
    myTfNestedForm.setEnabled(myNestedFormRadioButton.isSelected());
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

    public MyChooseClassActionListener(final Project project) {
      myProject = project;
    }

    public void actionPerformed(final ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      final TreeClassChooser chooser = factory.createInheritanceClassChooser(
        UIDesignerBundle.message("title.choose.component.class"),
        GlobalSearchScope.allScope(myProject),
        PsiManager.getInstance(myProject).findClass(JComponent.class.getName(), GlobalSearchScope.allScope(myProject)),
        true, true, null);
      chooser.showDialog();
      final PsiClass result = chooser.getSelectedClass();
      if (result != null) {
        setEditorText(result.getQualifiedName());
      }
    }
  }

  private static class MyChooseFileActionListener implements ActionListener {
    private final Project myProject;
    private TreeFileChooser.PsiFileFilter myFilter;
    private TextFieldWithBrowseButton myTextField;

    public MyChooseFileActionListener(final Project project,
                                      final TreeFileChooser.PsiFileFilter filter,
                                      final TextFieldWithBrowseButton textField) {
      myProject = project;
      myFilter = filter;
      myTextField = textField;
    }

    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
      PsiFile formFile = null;
      if (myTextField.getText().length() > 0) {
        VirtualFile formVFile = ModuleUtil.findResourceFileInProject(myProject, myTextField.getText());
        if (formVFile != null) {
          formFile = PsiManager.getInstance(myProject).findFile(formVFile);
        }
      }
      TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("add.component.choose.form"), formFile,
                                                              null, myFilter, true);
      fileChooser.showDialog();
      PsiFile file = fileChooser.getSelectedFile();
      if (file != null) {
        myTextField.setText(GuiEditorUtil.buildResourceName(file));
      }
    }
  }

  private class MyRadioChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      updateEnabledTextField();
    }
  }
}
