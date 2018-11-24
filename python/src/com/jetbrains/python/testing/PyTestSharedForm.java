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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import com.jetbrains.reflection.ReflectionUtilsKt;
import com.jetbrains.reflection.SimplePropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Form to display run configuration.
 * It displays target type, target, additional arguments, custom options (if provided) and environment options
 * Create with {@link #create(PyAbstractTestConfiguration, String...)}
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
  private TextFieldWithBrowseButton myTargetText;
  /**
   * Test label
   */
  private JBLabel myLabel;
  /**
   * Panel for custom options, specific for runner and for "Additional Arguments"al;sop
   */
  private JPanel myCustomOptionsPanel;
  private final ButtonGroup myButtonGroup = new ButtonGroup();
  private AbstractPyCommonOptionsForm myOptionsForm;

  private final Map<String, OptionHolder> myCustomOptions = new LinkedHashMap<>(); // TDO: Linked -- order

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
  public void setPropertyValue(@NotNull
                               final String propertyName, @Nullable
                               final String propertyValue) {
    myCustomOptions.get(propertyName).myOptionValue.setText(propertyValue != null ? propertyValue : "");
  }

  @Nullable
  @Override
  public String getPropertyValue(@NotNull
                                 final String propertyName) {
    return myCustomOptions.get(propertyName).myOptionValue.getText();
  }

  private PyTestSharedForm() {
  }

  /**
   * @param configuration configuration to configure form on creation
   * @param customOptions additional option names this form shall support. Make sure your configuration has appropriate properties.
   */
  @NotNull
  public static PyTestSharedForm create(@NotNull
                                           final PyAbstractTestConfiguration configuration,
                                        @NotNull
                                           final CustomOption... customOptions) { // TODO: DOC


    final PyTestSharedForm form = new PyTestSharedForm();
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(PythonFileType.INSTANCE);
    form.myTargetText.addBrowseFolderListener("Choose File or Folder", null, configuration.getProject(),
                                              descriptor);

    for (final TestTargetType testTargetType : TestTargetType.values()) {
      final JBRadioButton button = new JBRadioButton(StringUtil.capitalize(testTargetType.name().toLowerCase(Locale.getDefault())));
      button.setActionCommand(testTargetType.name());
      button.addActionListener(o -> form.configureElementsVisibility());
      form.myButtonGroup.add(button);
      form.myTargets.add(button);
    }
    form.myButtonGroup.getElements().nextElement().setSelected(true);

    form.myOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    final GridConstraints constraints = new GridConstraints();
    constraints.setFill(GridConstraints.FILL_BOTH);
    form.myOptionsPanel.add(form.myOptionsForm.getMainPanel(), constraints);


    form.myLabel.setText(configuration.getTestFrameworkName());


    form.addCustomOptions(
      ObjectArrays.concat(customOptions, new CustomOption(PyTestsSharedKt.getAdditionalArgumentsPropertyName(), TestTargetType.values()))
    );
    configuration.copyTo(ReflectionUtilsKt.getProperties(form, null, true));
    return form;
  }

  private void addCustomOptions(@NotNull
                                final CustomOption... customOptions) {
    if (customOptions.length == 0) {
      return;
    }
    final Map<String, JBTextField> optionValueFields = new HashMap<>();
    for (final CustomOption option : customOptions) {
      final JBTextField textField = new JBTextField();
      optionValueFields.put(option.myName, textField);
    }
    myCustomOptionsPanel.setLayout(new GridLayoutManager(customOptions.length, 2));

    for (int i = 0; i < customOptions.length; i++) {
      final CustomOption option = customOptions[i];
      final JBTextField textField = optionValueFields.get(option.myName);

      final GridConstraints labelConstraints = new GridConstraints();
      labelConstraints.setFill(GridConstraints.FILL_VERTICAL);
      labelConstraints.setRow(i);
      labelConstraints.setColumn(0);
      labelConstraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_SHRINK);

      final JLabel label = new JLabel(StringUtil.capitalize(CAPITAL_LETTER.matcher(option.myName).replaceAll(" ")));
      label.setHorizontalAlignment(SwingConstants.LEFT);
      myCustomOptionsPanel.add(label, labelConstraints);


      final GridConstraints textConstraints = new GridConstraints();
      textConstraints.setFill(GridConstraints.FILL_BOTH);
      textConstraints.setRow(i);
      textConstraints.setColumn(1);
      textConstraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW);
      myCustomOptionsPanel.add(textField, textConstraints);

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
    final String targetText = myTargetText.getText().trim();
    return getTargetType() == TestTargetType.PATH ? FileUtil.toSystemIndependentName(targetText) : targetText;
  }


  public void setTarget(@NotNull
                        final String targetText) {
    myTargetText.setText(targetText);
  }

  private void configureElementsVisibility() {
    final TestTargetType targetType = getTargetType();
    myTargetText.setVisible(targetType != TestTargetType.CUSTOM);
    myTargetText.getButton().setVisible(targetType == TestTargetType.PATH);

    for (final OptionHolder optionHolder : myCustomOptions.values()) {
      optionHolder.setType(targetType);
    }
  }

  @SuppressWarnings("WeakerAccess") // Accessor for property
  @NotNull
  public TestTargetType getTargetType() {
    return TestTargetType.valueOf(myButtonGroup.getSelection().getActionCommand());
  }

  @SuppressWarnings("unused") // Mutator for property
  public void setTargetType(@NotNull
                            final TestTargetType target) {
    final Enumeration<AbstractButton> elements = myButtonGroup.getElements();
    while (elements.hasMoreElements()) {
      final AbstractButton button = elements.nextElement();
      if (TestTargetType.valueOf(button.getActionCommand()) == target) {
        myButtonGroup.setSelected(button.getModel(), true);
        break;
      }
    }
    configureElementsVisibility();
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

    CustomOption(@NotNull
                 final String name,
                 @NotNull
                 final TestTargetType... supportedTypes) {
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

    private OptionHolder(@NotNull
                         final CustomOption option,
                         @NotNull
                         final JLabel optionLabel,
                         @NotNull
                         final JTextField optionValue) {
      myOption = option;
      myOptionLabel = optionLabel;
      myOptionValue = optionValue;
    }

    private void setType(@NotNull
                         final TestTargetType type) {
      final boolean visible = myOption.mySupportedTypes.contains(type);
      myOptionLabel.setVisible(visible);
      myOptionValue.setVisible(visible);
    }
  }
}