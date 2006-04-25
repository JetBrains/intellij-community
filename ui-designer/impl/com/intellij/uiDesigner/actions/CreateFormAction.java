/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

/**
 * @author yole
 */
public class CreateFormAction extends AbstractCreateFormAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.CreateFormAction");

  private String myLastClassName = null;

  public CreateFormAction() {
    super(UIDesignerBundle.message("action.gui.form.text"),
          UIDesignerBundle.message("action.gui.form.description"), Icons.UI_FORM_ICON);

    // delete obsolete template
    FileTemplateManager manager = FileTemplateManager.getInstance();
    //noinspection HardCodedStringLiteral
    final FileTemplate template = manager.getTemplate("GUI Form");
    //noinspection HardCodedStringLiteral
    if (template != null && template.getExtension().equals("form")) {
      manager.removeTemplate(template, false);
    }
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    final MyInputValidator validator = new MyInputValidator(project, directory);

    final DialogWrapper dialog = new MyDialog(project, validator);

    dialog.show();
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    if (myLastClassName != null) {
      directory.checkCreateClass(myLastClassName);
    }
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    PsiElement createdFile;
    PsiClass newClass = null;
    try {
      final PsiPackage aPackage = directory.getPackage();
      assert aPackage != null;
      final String packageName = aPackage.getQualifiedName();
      String fqClassName = null;
      if (myLastClassName != null) {
        fqClassName = packageName.length() == 0 ? newName : packageName + "." + myLastClassName;
      }

      final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/NewForm.xml");
      final PsiFile formFile = directory.getManager().getElementFactory().createFileFromText(newName + ".form", formBody);
      createdFile = directory.add(formFile);

      if (myLastClassName != null) {
        newClass = directory.createClass(myLastClassName);
      }
    }
    catch(IncorrectOperationException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return PsiElement.EMPTY_ARRAY;
    }

    if (newClass != null) {
      return new PsiElement[] { newClass.getContainingFile(), createdFile };
    }
    return new PsiElement[] { createdFile };
  }

  protected String getErrorTitle() {
    return UIDesignerBundle.message("error.cannot.create.form");
  }

  protected String getCommandName() {
    return UIDesignerBundle.message("command.create.form");
  }

  private class MyDialog extends DialogWrapper {
    private JPanel myTopPanel;
    private JTextField myFormNameTextField;
    private JCheckBox myCreateBoundClassCheckbox;
    private JTextField myClassNameTextField;
    private boolean myAdjusting = false;
    private boolean myNeedAdjust = true;

    private final MyInputValidator myValidator;

    public MyDialog(final Project project,
                    final MyInputValidator validator) {
      super(project, true);
      myValidator = validator;
      init();
      setTitle(UIDesignerBundle.message("title.new.gui.form"));
      setOKActionEnabled(false);

      myCreateBoundClassCheckbox.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          myClassNameTextField.setEnabled(myCreateBoundClassCheckbox.isSelected());
        }
      });

      myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          setOKActionEnabled(myFormNameTextField.getText().length() > 0);
          if (myNeedAdjust) {
            myAdjusting = true;
            myClassNameTextField.setText(myFormNameTextField.getText());
            myAdjusting = false;
          }
        }
      });

      myClassNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          if (!myAdjusting) {
            myNeedAdjust = false;
          }
        }
      });
    }

    protected JComponent createCenterPanel() {
      return myTopPanel;
    }

    protected void doOKAction() {
      if (myCreateBoundClassCheckbox.isSelected()) {
        myLastClassName = myClassNameTextField.getText();
      }
      else {
        myLastClassName = null;
      }
      final String inputString = myFormNameTextField.getText().trim();
      if (
        myValidator.checkInput(inputString) &&
        myValidator.canClose(inputString)
      ) {
        close(OK_EXIT_CODE);
      }
      close(OK_EXIT_CODE);
    }

    public JComponent getPreferredFocusedComponent() {
      return myFormNameTextField;
    }
  }
}
