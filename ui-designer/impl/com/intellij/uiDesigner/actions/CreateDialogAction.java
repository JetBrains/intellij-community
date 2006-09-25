package com.intellij.uiDesigner.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
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
    super(UIDesignerBundle.message("action.create.dialog"),
          UIDesignerBundle.message("action.description.create.dialog"), Icons.UI_FORM_ICON);
  }

  @NotNull
  protected PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    final MyInputValidator validator = new MyInputValidator(project, directory);

    final MyContentPane contentPane = new MyContentPane();

    final DialogWrapper dialog = new DialogWrapper(project, true) {
      {
        init();
        setTitle(UIDesignerBundle.message("title.new.dialog"));
      }
      protected JComponent createCenterPanel() {
        return contentPane.getPanel();
      }

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

      public JComponent getPreferredFocusedComponent() {
        return contentPane.myTfClassName;
      }
    };

    dialog.show();

    return validator.getCreatedElements();
  }

  protected String getCommandName() {
    return UIDesignerBundle.message("command.create.dialog");
  }

  protected void checkBeforeCreate(final String newName, final PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateClass(newName);
  }

  protected String getErrorTitle() {
    return UIDesignerBundle.message("error.cannot.create.dialog");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String createClassBody(
    final String className,
    final boolean generateOK,
    final boolean generateCancel,
    final boolean generateMain
  ) {
    final StringBuffer result = new StringBuffer(1024);

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
      result.append("\n");
      result.append(UIDesignerBundle.message("comment.call.onCancel.cross")).append("\n");
      result.append("setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);\n");
      result.append("addWindowListener(new java.awt.event.WindowAdapter() {\n");
      result.append("  public void windowClosing(java.awt.event.WindowEvent e) {\n");
      result.append("   onCancel();\n");
      result.append("  }\n");
      result.append("});\n");
      result.append("\n");
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
      result.append("private void onOK(){\n");
      result.append(UIDesignerBundle.message("comment.onok")).append("\n");
      result.append("dispose();\n");
      result.append("}\n");
    }

    if (generateCancel) {
      result.append("\n");
      result.append("private void onCancel(){\n");
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


  @NotNull
  protected PsiElement[] create(final String newName, final PsiDirectory directory) throws IncorrectOperationException {
    PsiFile sourceFile = directory.getManager().getElementFactory().createFileFromText(newName + ".java", createClassBody(newName, myRecentGenerateOK, myRecentGenerateCancel, myRecentGenerateMain));
    sourceFile = (PsiFile)directory.add(sourceFile);

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(directory.getProject());
    codeStyleManager.shortenClassReferences(sourceFile);
    codeStyleManager.reformat(sourceFile);

    final PsiPackage aPackage = directory.getPackage();
    final String packageName = aPackage.getQualifiedName();
    final String fqClassName = packageName.length() == 0 ? newName : packageName + "." + newName;

    final String formBody = createFormBody(directory.getProject(), fqClassName, "/com/intellij/uiDesigner/NewDialog.xml",
                                           GuiDesignerConfiguration.getInstance(directory.getProject()).DEFAULT_LAYOUT_MANAGER);
    final PsiFile formFile = directory.getManager().getElementFactory().createFileFromText(newName + ".form", formBody);
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

    public MyContentPane() {
    }

    public JPanel getPanel() {
      return myPanel;
    }
  }
}
