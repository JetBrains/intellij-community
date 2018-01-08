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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.JBUI;
import com.jetbrains.PySymbolFieldWithBrowseButton;
import com.jetbrains.extensions.python.FileChooserDescriptorExtKt;
import com.jetbrains.extenstions.ContextAnchor;
import com.jetbrains.extenstions.ModuleBasedContextAnchor;
import com.jetbrains.extenstions.ProjectSdkContextAnchor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyBrowseActionListener;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import com.jetbrains.reflection.ReflectionUtilsKt;
import com.jetbrains.reflection.SimplePropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Form to display run configuration.
 * It displays target type, target, additional arguments, custom options (if provided) and environment options
 * Create with {@link #create(PyAbstractTestConfiguration, CustomOption...)}}
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestSharedForm implements SimplePropertiesProvider {

  /**
   * Regex to convert additionalArgumentNames to "Additional Argument Names"
   */
  private static final Pattern CAPITAL_LETTER = Pattern.compile("(?=\\p{Upper})");

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
  public void setPropertyValue(@NotNull final String propertyName, @Nullable final String propertyValue) {
    myCustomOptions.get(propertyName).myOptionValue.setText(propertyValue != null ? propertyValue : "");
  }

  @Nullable
  @Override
  public String getPropertyValue(@NotNull final String propertyName) {
    return myCustomOptions.get(propertyName).myOptionValue.getText();
  }

  private PyTestSharedForm(@Nullable final Module module,
                           @NotNull final PyAbstractTestConfiguration configuration) {
    myPathTarget = new TextFieldWithBrowseButton();
    final Project project = configuration.getProject();

    myPathTarget.addBrowseFolderListener(new PyBrowseActionListener(configuration));
    final TypeEvalContext context = TypeEvalContext.userInitiated(project, null);
    final ThreeState testClassRequired = configuration.isTestClassRequired();


    ContextAnchor contentAnchor = (module != null ? new ModuleBasedContextAnchor(module) : new ProjectSdkContextAnchor(project, configuration.getSdk()));
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
      return LocalFileSystem.getInstance().findFileByPath(workingDirectory);
    });
  }

  /**
   * Titles border used among test run configurations
   */
  public static void setBorderToPanel(@NotNull final JPanel panel, @NotNull final String title) {
    panel.setBorder(IdeBorderFactory.createTitledBorder(title, false));
  }

  /**
   * @param configuration configuration to configure form on creation
   * @param customOptions additional option names this form shall support. Make sure your configuration has appropriate properties.
   */
  @NotNull
  public static PyTestSharedForm create(@NotNull final PyAbstractTestConfiguration configuration,
                                        @NotNull final CustomOption... customOptions) {
    final PyTestSharedForm form = new PyTestSharedForm(configuration.getModule(), configuration);

    for (final TestTargetType testTargetType : TestTargetType.values()) {
      final JBRadioButton button =
        new JBRadioButton(StringUtil.capitalize(testTargetType.getCustomName().toLowerCase(Locale.getDefault())));
      button.setActionCommand(testTargetType.name());
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
      ObjectArrays.concat(customOptions, new CustomOption(PyTestsSharedKt.getAdditionalArgumentsPropertyName(), TestTargetType.values()))
    );
    configuration.copyTo(ReflectionUtilsKt.getProperties(form, null, true));
    return form;
  }

  private void addCustomOptions(@NotNull final CustomOption... customOptions) {
    if (customOptions.length == 0) {
      return;
    }
    final Map<String, JBTextField> optionValueFields = new HashMap<>();
    for (final CustomOption option : customOptions) {
      final JBTextField textField = new JBTextField();
      optionValueFields.put(option.myName, textField);
    }

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = JBUI.insets(3);
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.LINE_START;

    for (final CustomOption option : customOptions) {
      final JBTextField textField = optionValueFields.get(option.myName);
      final JLabel label = new JLabel(StringUtil.capitalize(CAPITAL_LETTER.matcher(option.myName).replaceAll(" ") + ':'));
      label.setHorizontalAlignment(SwingConstants.LEFT);

      constraints.fill = GridBagConstraints.NONE;
      constraints.gridx = 0;
      constraints.weightx = 0;
      myCustomOptionsPanel.add(label, constraints);

      constraints.gridx = 1;
      constraints.weightx = 1.0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      myCustomOptionsPanel.add(textField, constraints);

      constraints.gridy++;

      myCustomOptions.put(option.myName, new OptionHolder(option, label, textField));
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
    return getTargetType() == TestTargetType.PATH ? FileUtil.toSystemIndependentName(targetText) : targetText;
  }


  public void setTarget(@NotNull final String targetText) {
    getActiveTextField().setText(targetText);
  }

  private void onTargetTypeChanged() {
    final TestTargetType targetType = getTargetType();

    for (final OptionHolder optionHolder : myCustomOptions.values()) {
      optionHolder.setType(targetType);
    }

    Arrays.stream(myPanelForTargetFields.getComponents()).forEach(myPanelForTargetFields::remove);
    final GridBagConstraints cons = new GridBagConstraints();
    cons.fill = GridBagConstraints.HORIZONTAL;
    cons.weightx = 1;

    if (targetType == TestTargetType.PATH) {
      myPanelForTargetFields.add(myPathTarget, cons);
    }
    else if (targetType == TestTargetType.PYTHON) {
      myPanelForTargetFields.add(myPythonTarget, cons);
    }
  }

  @NotNull
  private TextAccessor getActiveTextField() {
    return (getTargetType() == TestTargetType.PATH ? myPathTarget : myPythonTarget);
  }

  @SuppressWarnings("WeakerAccess") // Accessor for property
  @NotNull
  public TestTargetType getTargetType() {
    return TestTargetType.valueOf(myButtonGroup.getSelection().getActionCommand());
  }

  @SuppressWarnings("unused") // Mutator for property
  public void setTargetType(@NotNull final TestTargetType target) {
    final Enumeration<AbstractButton> elements = myButtonGroup.getElements();
    while (elements.hasMoreElements()) {
      final AbstractButton button = elements.nextElement();
      if (TestTargetType.valueOf(button.getActionCommand()) == target) {
        myButtonGroup.setSelected(button.getModel(), true);
        break;
      }
    }
    onTargetTypeChanged();
  }

  static final class CustomOption {
    /**
     * Option name
     */
    @NotNull
    private final String myName;
    /**
     * Types to display this option for
     */
    private final EnumSet<TestTargetType> mySupportedTypes;

    CustomOption(@NotNull final String name,
                 @NotNull final TestTargetType... supportedTypes) {
      myName = name;
      mySupportedTypes = EnumSet.copyOf(Arrays.asList(supportedTypes));
    }
  }

  private static final class OptionHolder {
    @NotNull
    private final CustomOption myOption;
    @NotNull
    private final JLabel myOptionLabel;
    @NotNull
    private final JTextField myOptionValue;

    private OptionHolder(@NotNull final CustomOption option,
                         @NotNull final JLabel optionLabel,
                         @NotNull final JTextField optionValue) {
      myOption = option;
      myOptionLabel = optionLabel;
      myOptionValue = optionValue;
    }

    private void setType(@NotNull final TestTargetType type) {
      final boolean visible = myOption.mySupportedTypes.contains(type);
      myOptionLabel.setVisible(visible);
      myOptionValue.setVisible(visible);
    }
  }
}