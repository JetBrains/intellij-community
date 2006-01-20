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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.GuiEditorUtil;
import com.intellij.uiDesigner.ImageFileFilter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  private JLabel myLblClass;
  private JLabel myLblIcon;
  private TextFieldWithBrowseButton myTfIconPath;
  private JCheckBox myChkHorCanShrink;
  private JCheckBox myChkHorCanGrow;
  private JCheckBox myChkHorWantGrow;
  private JCheckBox myChkVerCanShrink;
  private JCheckBox myChkVerCanGrow;
  private JCheckBox myChkVerWantGrow;
  private JPanel myClassNamePlaceholder;
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
    setEditorText(myItemToBeEdited.getClassName());
    myTfClassName.getButton().setEnabled(!project.isDefault()); // chooser should not work in default project
    myClassNamePlaceholder.setLayout(new BorderLayout());
    myClassNamePlaceholder.add(myTfClassName, BorderLayout.CENTER);

    myTfIconPath.setText(myItemToBeEdited.getIconPath());
    myTfIconPath.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(project);
        PsiFile iconFile = null;
        if (myItemToBeEdited.getIconPath() != null) {
          VirtualFile iconVFile = ModuleUtil.findResourceFileInProject(project, myItemToBeEdited.getIconPath());
          if (iconVFile != null) {
            iconFile = PsiManager.getInstance(project).findFile(iconVFile);
          }
        }
        TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("title.choose.icon.file"), iconFile,
                                                                null, new ImageFileFilter(null));
        fileChooser.showDialog();
        PsiFile file = fileChooser.getSelectedFile();
        if (file != null) {
          myTfIconPath.setText("/" + GuiEditorUtil.buildResourceName(file));
        }
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

    myLblClass.setLabelFor(myTfClassName);
    myLblIcon.setLabelFor(myTfIconPath);

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
    myItemToBeEdited.setClassName(myDocument.getText().trim());
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

  protected String getDimensionServiceKey() {
    return "#com.intellij.uiDesigner.palette.ComponentItemDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfClassName.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
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
        true, false, null);
      chooser.showDialog();
      final PsiClass result = chooser.getSelectedClass();
      if (result != null) {
        setEditorText(result.getQualifiedName());
      }
    }
  }
}
