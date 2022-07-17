// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CreateDialogAction extends AbstractCreateFormAction {
  private boolean myRecentGenerateOK;
  private boolean myRecentGenerateCancel;
  private boolean myRecentGenerateMain;

  public CreateDialogAction() {
    super(UIDesignerBundle.messagePointer("action.create.dialog"),
          UIDesignerBundle.messagePointer("action.description.create.dialog"), PlatformIcons.UI_FORM_ICON);
  }

  @Override
  protected PsiElement @NotNull [] invokeDialog(final @NotNull Project project, final @NotNull PsiDirectory directory) {
    final MyInputValidator validator = new JavaNameValidator(project, directory);

    final MyContentPane contentPane = new MyContentPane();

    final DialogWrapper dialog = new DialogWrapper(project, true) {
      {
        init();
        setTitle(UIDesignerBundle.message("title.new.dialog"));
      }
      @Override
      protected JComponent createCenterPanel() {
        return contentPane.getPanel();
      }

      @Override
      protected void doOKAction() {
        myRecentGenerateOK = contentPane.myChkGenerateOK.isSelected();
        myRecentGenerateCancel = contentPane.myChkGenerateCancel.isSelected();
        myRecentGenerateMain = contentPane.myChkGenerateMain.isSelected();

        final String inputString = contentPane.myTfClassName.getText().trim();
        if (
          validator.checkInput(inputString) &&
          validator.canClose(inputString)
        ) {
          close(OK_EXIT_CODE);
        }
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return contentPane.myTfClassName;
      }
    };

    dialog.show();

    return validator.getCreatedElements();
  }

  @Override
  protected String getErrorTitle() {
    return UIDesignerBundle.message("error.cannot.create.dialog");
  }

  private static String createClassBody(
    final String className,
    final boolean generateOK,
    final boolean generateCancel,
    final boolean generateMain
  ) {
    @NonNls final StringBuilder result = new StringBuilder(1024);

    result.append("public class ").append(className).append(" extends javax.swing.JDialog {\n");
    result.append("private javax.swing.JPanel contentPane;\n");
    result.append("private javax.swing.JButton buttonOK;\n");
    result.append("private javax.swing.JButton buttonCancel;\n");
    result.append("\n");
    result.append("public ").append(className).append("(){\n");
    result.append("setContentPane(contentPane);\n");
    result.append("setModal(true);\n");
    result.append("getRootPane().setDefaultButton(buttonOK);\n");

    if (generateOK) {
      result.append("\n");
      result.append("buttonOK.addActionListener(");
      result.append("new java.awt.event.ActionListener(){");
      result.append("public void actionPerformed(java.awt.event.ActionEvent e){");
      result.append("onOK();");
      result.append("}});\n");
    }

    if (generateCancel) {
      result.append("\n");
      result.append("buttonCancel.addActionListener(");
      result.append("new java.awt.event.ActionListener(){");
      result.append("public void actionPerformed(java.awt.event.ActionEvent e){");
      result.append("onCancel();");
      result.append("}});\n");
      result.append("\n ");
      result.append(UIDesignerBundle.message("comment.call.onCancel.cross")).append("\n");
      result.append("setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);\n");
      result.append("addWindowListener(new java.awt.event.WindowAdapter() {\n");
      result.append("  public void windowClosing(java.awt.event.WindowEvent e) {\n");
      result.append("   onCancel();\n");
      result.append("  }\n");
      result.append("});\n");
      result.append("\n ");
      result.append(UIDesignerBundle.message("comment.call.onCancel.escape")).append("\n");
      result.append("contentPane.registerKeyboardAction(");
      result.append("  new java.awt.event.ActionListener() {");
      result.append("    public void actionPerformed(java.awt.event.ActionEvent e) {");
      result.append("      onCancel();\n");
      result.append("    }");
      result.append("  },");
      result.append("  javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),");
      result.append("  javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT");
      result.append(");");
    }

    result.append("}\n");

    if (generateOK) {
      result.append("\n");
      result.append("private void onOK(){\n ");
      result.append(UIDesignerBundle.message("comment.onok")).append("\n");
      result.append("dispose();\n");
      result.append("}\n");
    }

    if (generateCancel) {
      result.append("\n");
      result.append("private void onCancel(){\n ");
      result.append(UIDesignerBundle.message("comment.oncancel")).append("\n");
      result.append("dispose();\n");
      result.append("}\n");
    }

    if (generateMain) {
      result.append("\n");
      result.append("public static void main(String[] args){\n");
      result.append(className).append(" dialog = new ").append(className).append("();\n");
      result.append("dialog.pack();\n");
      result.append("dialog.setVisible(true);\n");
      result.append("System.exit(0);\n");
      result.append("}\n");
    }

    result.append("}\n");

    return result.toString();
  }


  @Override
  protected PsiElement @NotNull [] create(@NotNull final String newName, final @NotNull PsiDirectory directory) throws IncorrectOperationException {
    PsiFile sourceFile = PsiFileFactory.getInstance(directory.getProject())
      .createFileFromText(newName + ".java", createClassBody(newName, myRecentGenerateOK, myRecentGenerateCancel, myRecentGenerateMain));
    sourceFile = (PsiFile)directory.add(sourceFile);

    JavaCodeStyleManager.getInstance(directory.getProject()).shortenClassReferences(sourceFile);
    CodeStyleManager.getInstance(directory.getProject()).reformat(sourceFile);

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    final String packageName = aPackage.getQualifiedName();
    final String fqClassName = packageName.length() == 0 ? newName : packageName + "." + newName;

    final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/NewDialog.xml",
                                           GuiDesignerConfiguration.getInstance(directory.getProject()).DEFAULT_LAYOUT_MANAGER);
    final PsiFile formFile = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(newName + ".form", formBody);
    PsiElement createdFile = directory.add(formFile);

    PsiClass[] classes = ((PsiJavaFile)sourceFile).getClasses();
    return new PsiElement[]{createdFile, classes[0]};
  }

  private static final class MyContentPane {
    private JPanel myPanel;
    private JCheckBox myChkGenerateCancel;
    private JCheckBox myChkGenerateOK;
    private JCheckBox myChkGenerateMain;
    private JTextField myTfClassName;

    MyContentPane() {
    }

    public JPanel getPanel() {
      return myPanel;
    }
  }
}
