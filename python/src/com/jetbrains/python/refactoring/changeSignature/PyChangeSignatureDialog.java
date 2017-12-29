/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.CallerChooserBase;
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.EditorTextFieldJBTableRowRenderer;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */

public class PyChangeSignatureDialog extends
                                     ChangeSignatureDialogBase<PyParameterInfo, PyFunction, String, PyMethodDescriptor, PyParameterTableModelItem, PyParameterTableModel> {

  public PyChangeSignatureDialog(Project project, PyMethodDescriptor method) {
    super(project, method, false, method.getMethod().getContext());
  }

  @Override
  protected LanguageFileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  protected PyParameterTableModel createParametersInfoModel(PyMethodDescriptor method) {
    final PyParameterList parameterList = PsiTreeUtil.getChildOfType(method.getMethod(), PyParameterList.class);
    return new PyParameterTableModel(parameterList, myDefaultValueContext, myProject);
  }

  @Override
  public BaseRefactoringProcessor createRefactoringProcessor() {
    final List<PyParameterInfo> parameters = getParameters();
    return new PyChangeSignatureProcessor(myProject, myMethod.getMethod(), getMethodName(),
                                          parameters.toArray(new PyParameterInfo[parameters.size()]));
  }

  @Nullable
  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    return null;
  }

  @Nullable
  @Override
  protected CallerChooserBase<PyFunction> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<PyFunction>> callback) {
    return null;
  }

  public boolean isNameValid(final String name, final Project project) {
    final NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());
    return name != null && validator.isIdentifier(name, project) && !validator.isKeyword(name, project);
  }

  @Nullable
  @Override
  protected String validateAndCommitData() {
    final String functionName = myNameField.getText().trim();
    if (!functionName.equals(myMethod.getName())) {
      final boolean defined = IntroduceValidator.isDefinedInScope(functionName, myMethod.getMethod());
      if (defined) {
        return PyBundle.message("refactoring.change.signature.dialog.validation.name.defined");
      }
      if (!isNameValid(functionName, myProject)) {
        return PyBundle.message("refactoring.change.signature.dialog.validation.function.name");
      }
    }
    final List<PyParameterTableModelItem> parameters = myParametersTableModel.getItems();
    final Set<String> parameterNames = new HashSet<>();
    boolean hadPositionalContainer = false;
    boolean hadKeywordContainer = false;
    boolean hadDefaultValue = false;
    boolean hadSingleStar = false;
    boolean hadParamsAfterSingleStar = false;
    final LanguageLevel languageLevel = LanguageLevel.forElement(myMethod.getMethod());

    final int parametersLength = parameters.size();

    for (int index = 0; index < parametersLength; index++) {
      final PyParameterTableModelItem info = parameters.get(index);
      final PyParameterInfo parameter = info.parameter;
      final String name = parameter.getName();
      final String nameWithoutStars = StringUtil.trimLeading(name, '*').trim();
      if (parameterNames.contains(nameWithoutStars)) {
        return PyBundle.message("ANN.duplicate.param.name");
      }
      parameterNames.add(nameWithoutStars);

      if (name.equals("*")) {
        hadSingleStar = true;
        if (index == parametersLength - 1) {
          return PyBundle.message("ANN.named.parameters.after.star");
        }
      }
      else if (name.startsWith("*") && !name.startsWith("**")) {
        if (hadKeywordContainer) {
          return PyBundle.message("ANN.starred.param.after.kwparam");
        }
        if (hadSingleStar || hadPositionalContainer) {
          return PyBundle.message("refactoring.change.signature.dialog.validation.multiple.star");
        }
        if (!isNameValid(name.substring(1), myProject)) {
          return PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name");
        }
        hadPositionalContainer = true;
      }
      else if (name.startsWith("**")) {
        if (hadSingleStar && !hadParamsAfterSingleStar) {
          return PyBundle.message("ANN.named.parameters.after.star");
        }
        if (hadKeywordContainer) {
          return PyBundle.message("refactoring.change.signature.dialog.validation.multiple.double.star");
        }
        if (!isNameValid(name.substring(2), myProject)) {
          return PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name");
        }
        hadKeywordContainer = true;
      }
      else {
        if (!isNameValid(name, myProject)) {
          return PyBundle.message("refactoring.change.signature.dialog.validation.parameter.name");
        }
        if (hadSingleStar) {
          hadParamsAfterSingleStar = true;
        }
        if (hadPositionalContainer && languageLevel.isPython2()) {
          return PyBundle.message("ANN.regular.param.after.vararg");
        }
        else if (hadKeywordContainer) {
          return PyBundle.message("ANN.regular.param.after.keyword");
        }
        final String defaultValue = info.getDefaultValue();
        if (defaultValue != null && !StringUtil.isEmptyOrSpaces(defaultValue) && parameter.getDefaultInSignature()) {
          hadDefaultValue = true;
        }
        else {
          if (hadDefaultValue && !hadSingleStar && (languageLevel.isPython2() || !hadPositionalContainer)) {
            return PyBundle.message("ANN.non.default.param.after.default");
          }
        }
      }
      if (parameter.getOldIndex() < 0) {
        if (!parameter.getName().startsWith("*")) {
          if (StringUtil.isEmpty(info.defaultValueCodeFragment.getText())) {
            return PyBundle.message("refactoring.change.signature.dialog.validation.default.missing");
          }
          if (StringUtil.isEmptyOrSpaces(parameter.getName())) {
            return PyBundle.message("refactoring.change.signature.dialog.validation.parameter.missing");
          }
        }
      }
      else if (myMethod.getParameters().get(parameter.getOldIndex()).getDefaultInSignature() &&
               StringUtil.isEmptyOrSpaces(parameter.getDefaultValue())) {
        return PyBundle.message("refactoring.change.signature.dialog.validation.default.missing");
      }
    }

    return null;
  }

  @Override
  protected ValidationInfo doValidate() {
    final String message = validateAndCommitData();
    getRefactorAction().setEnabled(message == null);
    getPreviewAction().setEnabled(message == null);
    if (message != null) return new ValidationInfo(message);
    return super.doValidate();
  }

  @Override
  protected String calculateSignature() {
    final StringBuilder builder = new StringBuilder();
    builder.append(getMethodName());
    builder.append("(");
    final List<PyParameterTableModelItem> parameters = myParametersTableModel.getItems();
    for (int i = 0; i < parameters.size(); i++) {
      final PyParameterTableModelItem parameterInfo = parameters.get(i);
      builder.append(parameterInfo.parameter.getName());
      final String defaultValue = parameterInfo.defaultValueCodeFragment.getText();
      if (!defaultValue.isEmpty() && parameterInfo.isDefaultInSignature()) {
        builder.append(" = ").append(defaultValue);
      }
      if (i != parameters.size() - 1) {
        builder.append(", ");
      }
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
    return new ComboBoxVisibilityPanel<>(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  protected ParametersListTable createParametersListTable() {
    return new ParametersListTable() {
      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return new EditorTextFieldJBTableRowRenderer(getProject(), getFileType(), getDisposable()) {
          @Override
          protected String getText(JTable table, int row) {
            final PyParameterTableModelItem pyItem = getRowItem(row);
            final StringBuilder text = new StringBuilder(pyItem.parameter.getName());
            final String defaultCallValue = pyItem.defaultValueCodeFragment.getText();
            final String defaultValue = pyItem.isDefaultInSignature() ? pyItem.defaultValueCodeFragment.getText() : "";

            if (StringUtil.isNotEmpty(defaultValue)) {
              text.append(" = ").append(defaultValue);
            }

            if (StringUtil.isNotEmpty(defaultCallValue)) {
              text.append(" // default value = ").append(defaultCallValue);
            }
            return text.toString();
          }
        };
      }

      @NotNull
      @Override
      protected JBTableRowEditor getRowEditor(ParameterTableModelItemBase<PyParameterInfo> item) {
        return new JBTableRowEditor() {
          private EditorTextField myNameEditor;
          private EditorTextField myDefaultValueEditor;
          private JCheckBox myDefaultInSignature;

          @Override
          public void prepareEditor(JTable table, int row) {
            setLayout(new GridLayout(1, 3));
            final JPanel parameterPanel = createParameterPanel();
            add(parameterPanel);
            final JPanel defaultValuePanel = createDefaultValuePanel();
            add(defaultValuePanel);
            final JPanel defaultValueCheckBox = createDefaultValueCheckBox();
            add(defaultValueCheckBox);

            final String nameText = myNameEditor.getText();
            myDefaultValueEditor.setEnabled(!nameText.startsWith("*") && !PyNames.CANONICAL_SELF.equals(nameText));
            myDefaultInSignature.setEnabled(!nameText.startsWith("*") && !PyNames.CANONICAL_SELF.equals(nameText));
          }

          private JPanel createDefaultValueCheckBox() {
            final JPanel defaultValuePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));

            final JBLabel inSignatureLabel = new JBLabel(PyBundle.message("refactoring.change.signature.dialog.default.value.checkbox"),
                                                         UIUtil.ComponentStyle.SMALL);
            IJSwingUtilities.adjustComponentsOnMac(inSignatureLabel,
                                                   myDefaultInSignature);
            defaultValuePanel.add(inSignatureLabel, BorderLayout.WEST);
            myDefaultInSignature = new JCheckBox();
            myDefaultInSignature.setSelected(((PyParameterTableModelItem)item).isDefaultInSignature());
            myDefaultInSignature.addItemListener(new ItemListener() {
              @Override
              public void itemStateChanged(ItemEvent event) {
                ((PyParameterTableModelItem)item).setDefaultInSignature(myDefaultInSignature.isSelected());
              }
            });
            myDefaultInSignature.addChangeListener(mySignatureUpdater);
            myDefaultInSignature.setEnabled(item.parameter.getOldIndex() == -1);
            defaultValuePanel.add(myDefaultInSignature, BorderLayout.EAST);
            return defaultValuePanel;
          }

          private JPanel createDefaultValuePanel() {
            final JPanel defaultValuePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
            final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(item.defaultValueCodeFragment);
            myDefaultValueEditor = new EditorTextField(doc, getProject(), getFileType());
            final JBLabel defaultValueLabel = new JBLabel(PyBundle.message("refactoring.change.signature.dialog.default.value.label"),
                                                          UIUtil.ComponentStyle.SMALL);
            IJSwingUtilities.adjustComponentsOnMac(defaultValueLabel, myDefaultValueEditor);
            defaultValuePanel.add(defaultValueLabel);
            defaultValuePanel.add(myDefaultValueEditor);
            myDefaultValueEditor.setPreferredWidth(getTable().getWidth() / 2);
            myDefaultValueEditor.addDocumentListener(mySignatureUpdater);
            return defaultValuePanel;
          }

          private JPanel createParameterPanel() {
            final JPanel namePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
            myNameEditor = new EditorTextField(item.parameter.getName(), getProject(), getFileType());
            final JBLabel nameLabel = new JBLabel(PyBundle.message("refactoring.change.signature.dialog.name.label"),
                                                  UIUtil.ComponentStyle.SMALL);
            IJSwingUtilities.adjustComponentsOnMac(nameLabel, myNameEditor);
            namePanel.add(nameLabel);
            namePanel.add(myNameEditor);
            myNameEditor.setPreferredWidth(getTable().getWidth() / 2);
            myNameEditor.addDocumentListener(new DocumentListener() {
              @Override
              public void documentChanged(DocumentEvent event) {
                fireDocumentChanged(event, 0);
                myDefaultValueEditor.setEnabled(!myNameEditor.getText().startsWith("*"));
                myDefaultInSignature.setEnabled(!myNameEditor.getText().startsWith("*"));
              }
            });

            myNameEditor.addDocumentListener(mySignatureUpdater);
            return namePanel;
          }

          @Override
          public JBTableRow getValue() {
            return new JBTableRow() {
              @Override
              public Object getValueAt(int column) {
                switch (column) {
                  case 0:
                    return myNameEditor.getText().trim();
                  case 1:
                    return new Pair<>(item.defaultValueCodeFragment,
                                      ((PyParameterTableModelItem)item).isDefaultInSignature());
                }
                return null;
              }
            };
          }

          @Override
          public JComponent getPreferredFocusedComponent() {
            return myNameEditor.getFocusTarget();
          }

          @Override
          public JComponent[] getFocusableComponents() {
            final List<JComponent> focusable = new ArrayList<>();
            focusable.add(myNameEditor.getFocusTarget());
            if (myDefaultValueEditor != null) {
              focusable.add(myDefaultValueEditor.getFocusTarget());
            }
            if (myDefaultInSignature != null) {
              focusable.add(myDefaultInSignature);
            }
            return focusable.toArray(new JComponent[focusable.size()]);
          }
        };
      }

      @Override
      protected boolean isRowEmpty(int row) {
        return false;
      }
    };
  }

  @Override
  protected boolean isListTableViewSupported() {
    return true;
  }

  @Override
  protected boolean mayPropagateParameters() {
    return false;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }
}
