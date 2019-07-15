// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.tox;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Ilya.Kazakevich
 */
final class PyToxConfigurationSettings extends SettingsEditor<PyToxConfiguration> {
  private static final Pattern ARG_SEPARATOR = Pattern.compile("\\s+");
  @NotNull
  private final Project myProject;
  private AbstractPyCommonOptionsForm myForm;
  private JPanel myPanel;
  private JTextField myArgumentsField;
  private JTextField myRunOnlyTestsField;

  PyToxConfigurationSettings(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  protected void applyEditorTo(@NotNull final PyToxConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(myForm, s);
    s.setArguments(asArray(myArgumentsField));
    s.setRunOnlyEnvs(asArray(myRunOnlyTestsField));
  }

  @NotNull
  private static String[] asArray(@NotNull final JTextComponent field) {
    final String text = field.getText();
    if (text.isEmpty()) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    return ARG_SEPARATOR.split(text);
  }

  @Override
  protected void resetEditorFrom(@NotNull final PyToxConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myForm);
    myArgumentsField.setText(fromArray(s.getArguments()));
    myRunOnlyTestsField.setText(fromArray(s.getRunOnlyEnvs()));
  }

  @Nullable
  private static String fromArray(@NotNull final String... arguments) {
    return StringUtil.join(arguments, " ");
  }

  @NotNull
  @Override
  protected JComponent createEditor() {

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.PAGE_START);

    myForm = createEnvPanel();
    final JComponent envPanel = myForm.getMainPanel();

    panel.add(envPanel, BorderLayout.PAGE_END);

    return panel;
  }

  @NotNull
  private AbstractPyCommonOptionsForm createEnvPanel() {
    return PyCommonOptionsFormFactory.getInstance().createForm(new PyCommonOptionsFormData() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public List<Module> getValidModules() {
        return AbstractPythonRunConfiguration.getValidModules(myProject);
      }

      @Override
      public boolean showConfigureInterpretersLink() {
        return false;
      }
    });
  }
}
