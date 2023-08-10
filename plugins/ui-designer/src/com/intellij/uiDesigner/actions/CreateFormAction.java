// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;


public class CreateFormAction extends AbstractCreateFormAction {
  private static final Logger LOG = Logger.getInstance(CreateFormAction.class);

  private String myLastClassName = null;
  private String myLastLayoutManager = null;

  public CreateFormAction() {
    super(UIDesignerBundle.messagePointer("action.gui.form.text"),
          UIDesignerBundle.messagePointer("action.gui.form.description"), PlatformIcons.UI_FORM_ICON);
  }

  @Override
  protected PsiElement @NotNull [] invokeDialog(@NotNull Project project, @NotNull PsiDirectory directory) {
    final MyInputValidator validator = new JavaNameValidator(project, directory);

    final DialogWrapper dialog = new MyDialog(project, validator);

    dialog.show();
    return validator.getCreatedElements();
  }

  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) throws Exception {
    PsiElement createdFile;
    PsiClass newClass = null;
    try {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      assert aPackage != null;
      final String packageName = aPackage.getQualifiedName();
      String fqClassName = null;
      if (myLastClassName != null) {
        fqClassName = packageName.length() == 0 ? myLastClassName : packageName + "." + myLastClassName;
      }

      final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/NewForm.xml",
                                             myLastLayoutManager);
      @NonNls final String fileName = newName + ".form";
      final PsiFile formFile = PsiFileFactory.getInstance(directory.getProject())
        .createFileFromText(fileName, GuiFormFileType.INSTANCE, formBody);
      createdFile = directory.add(formFile);

      if (myLastClassName != null) {
        newClass = JavaDirectoryService.getInstance().createClass(directory, myLastClassName);
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

  @Override
  protected String getErrorTitle() {
    return UIDesignerBundle.message("error.cannot.create.form");
  }

  private class MyDialog extends DialogWrapper {
    private JPanel myTopPanel;
    private JTextField myFormNameTextField;
    private JCheckBox myCreateBoundClassCheckbox;
    private JTextField myClassNameTextField;
    private TemplateKindCombo myBaseLayoutManagerCombo;
    private JLabel myUpDownHintForm;
    private boolean myAdjusting = false;
    private boolean myNeedAdjust = true;

    private final Project myProject;
    private final MyInputValidator myValidator;

    MyDialog(final Project project,
                    final MyInputValidator validator) {
      super(project, true);
      myProject = project;
      myValidator = validator;
      myBaseLayoutManagerCombo.registerUpDownHint(myFormNameTextField);
      myUpDownHintForm.setIcon(PlatformIcons.UP_DOWN_ARROWS);
      init();
      setTitle(UIDesignerBundle.message("title.new.gui.form"));
      setOKActionEnabled(false);

      myCreateBoundClassCheckbox.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          myClassNameTextField.setEnabled(myCreateBoundClassCheckbox.isSelected());
        }
      });

      myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          setOKActionEnabled(myFormNameTextField.getText().length() > 0);
          if (myNeedAdjust) {
            myAdjusting = true;
            myClassNameTextField.setText(myFormNameTextField.getText());
            myAdjusting = false;
          }
        }
      });

      myClassNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          if (!myAdjusting) {
            myNeedAdjust = false;
          }
        }
      });

      for (String layoutName: LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames()) {
        String displayName = LayoutManagerRegistry.getLayoutManagerDisplayName(layoutName);
        myBaseLayoutManagerCombo.addItem(displayName, null, layoutName);
      }
      myBaseLayoutManagerCombo.setSelectedName(GuiDesignerConfiguration.getInstance(project).DEFAULT_LAYOUT_MANAGER);
    }

    @Override
    protected JComponent createCenterPanel() {
      return myTopPanel;
    }

    @Override
    protected void doOKAction() {
      if (myCreateBoundClassCheckbox.isSelected()) {
        myLastClassName = myClassNameTextField.getText();
      }
      else {
        myLastClassName = null;
      }
      myLastLayoutManager = myBaseLayoutManagerCombo.getSelectedName();
      GuiDesignerConfiguration.getInstance(myProject).DEFAULT_LAYOUT_MANAGER = myLastLayoutManager;
      final String inputString = myFormNameTextField.getText().trim();
      if (myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        close(OK_EXIT_CODE);
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myFormNameTextField;
    }
  }
}
