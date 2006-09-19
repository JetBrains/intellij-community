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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
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
  private String myLastLayoutManager = null;

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

      final String formBody = createFormBody(directory.getProject(), fqClassName, "/com/intellij/uiDesigner/NewForm.xml",
                                             myLastLayoutManager);
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
    private JComboBox myBaseLayoutManagerCombo;
    private boolean myAdjusting = false;
    private boolean myNeedAdjust = true;

    private final Project myProject;
    private final MyInputValidator myValidator;

    public MyDialog(final Project project,
                    final MyInputValidator validator) {
      super(project, true);
      myProject = project;
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

      myBaseLayoutManagerCombo.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames()));
      myBaseLayoutManagerCombo.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          append(LayoutManagerRegistry.getLayoutManagerDisplayName((String) value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
      myBaseLayoutManagerCombo.setSelectedItem(GuiDesignerConfiguration.getInstance(project).DEFAULT_LAYOUT_MANAGER);
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
      myLastLayoutManager = (String)myBaseLayoutManagerCombo.getSelectedItem();
      GuiDesignerConfiguration.getInstance(myProject).DEFAULT_LAYOUT_MANAGER = myLastLayoutManager;
      final String inputString = myFormNameTextField.getText().trim();
      if (myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        close(OK_EXIT_CODE);
      }
      close(OK_EXIT_CODE);
    }

    public JComponent getPreferredFocusedComponent() {
      return myFormNameTextField;
    }
  }
}
