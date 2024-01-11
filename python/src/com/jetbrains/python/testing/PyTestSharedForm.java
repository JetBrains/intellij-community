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
package com.jetbrains.python.testing;

import com.google.common.collect.ObjectArrays;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PySymbolFieldWithBrowseButton;
import com.jetbrains.python.extensions.ContextAnchor;
import com.jetbrains.python.extensions.ModuleBasedContextAnchor;
import com.jetbrains.python.extensions.ProjectSdkContextAnchor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.reflection.ReflectionUtilsKt;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyBrowseActionListener;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.reflection.SimplePropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.jetbrains.python.run.PythonScriptCommandLineState.getExpandedWorkingDir;

/**
 * Form to display run configuration.
 * It displays target type, target, additional arguments, custom options (if provided) and environment options
 * Create with {@link #create(PyAbstractTestConfiguration, PyTestCustomOption...)}}
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestSharedForm implements SimplePropertiesProvider {

  private JPanel myPanel;
  /**
   * Panel for test targets
   */
  private JPanel myTargets;
  /**
   * Panel for environment options
   */
  private JPanel myOptionsPanel;
  /**
   * Panel for custom options, specific for runner and for "Additional Arguments"al;sop
   */
  private JPanel myCustomOptionsPanel;
  private JPanel myPanelForTargetFields;
  private final ButtonGroup myButtonGroup = new ButtonGroup();
  private AbstractPyCommonOptionsForm myOptionsForm;

  private final Map<String, OptionHolder> myCustomOptions = new LinkedHashMap<>(); // TDO: Linked -- order
  private final TextFieldWithBrowseButton myPathTarget;
  private final PySymbolFieldWithBrowseButton myPythonTarget;

  @NotNull
  JPanel getPanel() {
    return myPanel;
  }

  @NotNull
  @Override
  public List<String> getPropertyNames() {
    return new ArrayList<>(myCustomOptions.keySet());
  }

  @Override
  public void setPropertyValue(@NotNull final String propertyName, @Nullable final Object propertyValue) {
    myCustomOptions.get(propertyName).setValue(propertyValue);
  }

  @Nullable
  @Override
  public Object getPropertyValue(@NotNull final String propertyName) {
    return myCustomOptions.get(propertyName).getValue();
  }

  private PyTestSharedForm(@Nullable final Module module,
                           @NotNull final PyAbstractTestConfiguration configuration) {
    myPathTarget = new TextFieldWithBrowseButton();
    final Project project = configuration.getProject();

    myPathTarget.addBrowseFolderListener(new PyBrowseActionListener(configuration));
    final TypeEvalContext context = TypeEvalContext.userInitiated(project, null);
    final ThreeState testClassRequired = configuration.isTestClassRequired();


    ContextAnchor contentAnchor =
      (module != null ? new ModuleBasedContextAnchor(module) : new ProjectSdkContextAnchor(project, configuration.getSdk()));
    myPythonTarget = new PySymbolFieldWithBrowseButton(contentAnchor,
                                                       element -> {
                                                         if (element instanceof PsiDirectory) {
                                                           // Folder is always accepted because we can't be sure
                                                           // if it is test-enabled or not
                                                           return true;
                                                         }
                                                         return PyTestsSharedKt.isTestElement(element, testClassRequired, context);
                                                       }, () -> {
      final String workingDirectory = configuration.getWorkingDirectory();
      if (StringUtil.isEmpty(workingDirectory)) {
        return null;
      }
      return LocalFileSystem.getInstance().findFileByPath(getExpandedWorkingDir(configuration));
    });
  }

  /**
   * Titles border used among test run configurations
   */
  public static void setBorderToPanel(@NotNull final JPanel panel, @NotNull final @NlsSafe String title) {
    panel.setBorder(IdeBorderFactory.createTitledBorder(title, false));
  }

  /**
   * @param configuration configuration to configure form on creation
   * @param customOptions additional option names this form shall support. Make sure your configuration has appropriate properties.
   */
  @NotNull
  public static PyTestSharedForm create(@NotNull final PyAbstractTestConfiguration configuration,
                                        final PyTestCustomOption @NotNull ... customOptions) {
    final PyTestSharedForm form = new PyTestSharedForm(configuration.getModule(), configuration);

    for (final PyRunTargetVariant testTargetType : PyRunTargetVariant.values()) {
      final JBRadioButton button =
        new JBRadioButton(StringUtil.capitalize(testTargetType.getCustomName().toLowerCase(Locale.getDefault())));
      button.setActionCommand(testTargetType.name()); // NON-NLS
      button.addActionListener(o -> form.onTargetTypeChanged());
      form.myButtonGroup.add(button);
      form.myTargets.add(button);
    }
    form.myButtonGroup.getElements().nextElement().setSelected(true);

    form.myOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    final GridConstraints constraints = new GridConstraints();
    constraints.setFill(GridConstraints.FILL_BOTH);
    form.myOptionsPanel.add(form.myOptionsForm.getMainPanel(), constraints);

    setBorderToPanel(form.myPanel, configuration.getTestFrameworkName());

    form.addCustomOptions(
      ObjectArrays.concat(customOptions, new PyTestCustomOption(
        PyTestsSharedKt.getAdditionalArgumentsProperty(),
        PyRunTargetVariant.values()))
    );
    configuration.copyTo(ReflectionUtilsKt.getProperties(form, null, true));
    return form;
  }

  private void addCustomOptions(final PyTestCustomOption @NotNull ... customOptions) {
    if (customOptions.length == 0) {
      return;
    }
    final Map<String, JComponent> optionValueFields = new HashMap<>();
    for (final PyTestCustomOption option : customOptions) {
      final JComponent textField = option.isBooleanType() ? new JBCheckBox() : new JBTextField();
      optionValueFields.put(option.getName(), textField);
    }

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = JBUI.insets(3);
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.LINE_START;

    for (final PyTestCustomOption option : customOptions) {
      final JComponent field = optionValueFields.get(option.getName());
      final JLabel label = new JLabel(option.getLocalizedName()); // NON-NLS
      label.setHorizontalAlignment(SwingConstants.LEFT);

      constraints.fill = GridBagConstraints.NONE;
      constraints.gridx = 0;
      constraints.weightx = 0;
      myCustomOptionsPanel.add(label, constraints);

      constraints.gridx = 1;
      constraints.weightx = 1.0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      myCustomOptionsPanel.add(field, constraints);

      constraints.gridy++;

      OptionHolder value = option.isBooleanType()
                           ? new OptionHolder(option, label, (JBCheckBox)field)
                           : new OptionHolder(option, label, (JBTextField)field);

      myCustomOptions.put(option.getName(), value);
    }
  }

  @NotNull
  AbstractPyCommonOptionsForm getOptionsForm() {
    return myOptionsForm;
  }

  @NotNull
  public String getTarget() {
    // We should always use system-independent path because only this type of path is processed correctly
    // when stored (folder changed to macros to prevent hard code)
    final String targetText = getActiveTextField().getText().trim();
    return getTargetType() == PyRunTargetVariant.PATH ? FileUtil.toSystemIndependentName(targetText) : targetText;
  }


  public void setTarget(@NotNull final String targetText) {
    getActiveTextField().setText(targetText);
  }

  private void onTargetTypeChanged() {
    final PyRunTargetVariant targetType = getTargetType();

    for (final OptionHolder optionHolder : myCustomOptions.values()) {
      optionHolder.setType(targetType);
    }

    Arrays.stream(myPanelForTargetFields.getComponents()).forEach(myPanelForTargetFields::remove);
    final GridBagConstraints cons = new GridBagConstraints();
    cons.fill = GridBagConstraints.HORIZONTAL;
    cons.weightx = 1;

    if (targetType == PyRunTargetVariant.PATH) {
      myPanelForTargetFields.add(myPathTarget, cons);
    }
    else if (targetType == PyRunTargetVariant.PYTHON) {
      myPanelForTargetFields.add(myPythonTarget, cons);
    }
  }

  @NotNull
  private TextAccessor getActiveTextField() {
    return (getTargetType() == PyRunTargetVariant.PATH ? myPathTarget : myPythonTarget);
  }

  @SuppressWarnings("WeakerAccess") // Accessor for property
  @NotNull
  public PyRunTargetVariant getTargetType() {
    return PyRunTargetVariant.valueOf(myButtonGroup.getSelection().getActionCommand());
  }

  @SuppressWarnings("unused") // Mutator for property
  public void setTargetType(@NotNull final PyRunTargetVariant target) {
    final Enumeration<AbstractButton> elements = myButtonGroup.getElements();
    while (elements.hasMoreElements()) {
      final AbstractButton button = elements.nextElement();
      if (PyRunTargetVariant.valueOf(button.getActionCommand()) == target) {
        myButtonGroup.setSelected(button.getModel(), true);
        break;
      }
    }
    onTargetTypeChanged();
  }
}
