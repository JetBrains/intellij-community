// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

final class CreateDialogAction extends AbstractCreateFormAction {
  private boolean myRecentGenerateOK;
  private boolean myRecentGenerateCancel;
  private boolean myRecentGenerateMain;

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
    final @NonNls StringBuilder result = new StringBuilder(1024);

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
  protected PsiElement @NotNull [] create(final @NotNull String newName, final @NotNull PsiDirectory directory) throws IncorrectOperationException {
    PsiFile sourceFile = PsiFileFactory.getInstance(directory.getProject())
      .createFileFromText(newName + ".java", createClassBody(newName, myRecentGenerateOK, myRecentGenerateCancel, myRecentGenerateMain));
    sourceFile = (PsiFile)directory.add(sourceFile);

    JavaCodeStyleManager.getInstance(directory.getProject()).shortenClassReferences(sourceFile);
    CodeStyleManager.getInstance(directory.getProject()).reformat(sourceFile);

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    final String packageName = aPackage.getQualifiedName();
    final String fqClassName = packageName.isEmpty() ? newName : packageName + "." + newName;

    final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/NewDialog.xml",
                                           GuiDesignerConfiguration.getInstance(directory.getProject()).DEFAULT_LAYOUT_MANAGER);
    final PsiFile formFile = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(newName + ".form", formBody);
    PsiElement createdFile = directory.add(formFile);

    PsiClass[] classes = ((PsiJavaFile)sourceFile).getClasses();
    return new PsiElement[]{createdFile, classes[0]};
  }

  private static final class MyContentPane {
    private final JPanel myPanel;
    private final JCheckBox myChkGenerateCancel;
    private final JCheckBox myChkGenerateOK;
    private final JCheckBox myChkGenerateMain;
    private final JTextField myTfClassName;

    MyContentPane() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "edit.dialog.class.name"));
        myPanel.add(label1,
                    new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                        GridConstraints.SIZEPOLICY_FIXED,
                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myTfClassName = new JTextField();
        myPanel.add(myTfClassName, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                       new Dimension(150, -1), null, 0, false));
        myChkGenerateMain = new JCheckBox();
        myChkGenerateMain.setSelected(true);
        this.$$$loadButtonText$$$(myChkGenerateMain,
                                  this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "checkbox.generate.main"));
        myPanel.add(myChkGenerateMain, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myChkGenerateOK = new JCheckBox();
        myChkGenerateOK.setSelected(true);
        this.$$$loadButtonText$$$(myChkGenerateOK, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "checkbox.generate.ok"));
        myPanel.add(myChkGenerateOK, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myChkGenerateCancel = new JCheckBox();
        myChkGenerateCancel.setFocusTraversalPolicyProvider(true);
        myChkGenerateCancel.setSelected(true);
        this.$$$loadButtonText$$$(myChkGenerateCancel,
                                  this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "checkbox.generate.cancel"));
        myPanel.add(myChkGenerateCancel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      }
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
    public JComponent $$$getRootComponent$$$() { return myPanel; }

    public JPanel getPanel() {
      return myPanel;
    }
  }
}
