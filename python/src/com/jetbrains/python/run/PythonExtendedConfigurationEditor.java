/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Koshevoy
 */
public class PythonExtendedConfigurationEditor<T extends AbstractPythonRunConfiguration<T>> extends SettingsEditor<T> {
  @NotNull private final SettingsEditor<T> myMainSettingsEditor;

  private PyRunConfigurationEditorExtension myCurrentEditor;
  private SettingsEditor<AbstractPythonRunConfiguration> myCurrentSettingsEditor;
  private JComponent myCurrentSettingsEditorComponent;

  private JPanel mySettingsPlaceholder;

  public PythonExtendedConfigurationEditor(@NotNull SettingsEditor<T> editor) {
    myMainSettingsEditor = editor;

    Disposer.register(this, myMainSettingsEditor);
  }

  @Override
  protected void resetEditorFrom(T s) {
    myMainSettingsEditor.resetFrom(s);
    updateCurrentEditor(s);
    if (myCurrentSettingsEditor != null) {
      myCurrentSettingsEditor.resetFrom(s);
    }
  }

  @Override
  protected void applyEditorTo(T s) throws ConfigurationException {
    myMainSettingsEditor.applyTo(s);
    boolean updated = updateCurrentEditor(s);
    if (myCurrentSettingsEditor != null) {
      if (updated) {
        myCurrentSettingsEditor.resetFrom(s);
      }
      else {
        myCurrentSettingsEditor.applyTo(s);
      }
    }
  }

  private boolean updateCurrentEditor(T s) {
    PyRunConfigurationEditorExtension newEditor = PyRunConfigurationEditorExtension.Factory.getExtension(s);
    if (myCurrentEditor != newEditor) {
      // discard previous
      if (myCurrentSettingsEditorComponent != null) {
        mySettingsPlaceholder.remove(myCurrentSettingsEditorComponent);
      }
      if (myCurrentSettingsEditor != null) {
        Disposer.dispose(myCurrentSettingsEditor);
      }
      // set current editor
      myCurrentEditor = newEditor;
      myCurrentSettingsEditor = null;
      // add new
      if (newEditor != null) {
        myCurrentSettingsEditor = newEditor.createEditor(s);
        myCurrentSettingsEditorComponent = myCurrentSettingsEditor.getComponent();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.insets.top = 10;
        mySettingsPlaceholder.add(myCurrentSettingsEditorComponent, constraints);

        Disposer.register(this, myCurrentSettingsEditor);
      }
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    JComponent mainEditorComponent = myMainSettingsEditor.getComponent();
    mySettingsPlaceholder = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0;
    mySettingsPlaceholder.add(mainEditorComponent, constraints);
    return mySettingsPlaceholder;
  }

  @NotNull
  public static <T extends AbstractPythonRunConfiguration<T>> PythonExtendedConfigurationEditor<T> create(@NotNull SettingsEditor<T> editor) {
    return new PythonExtendedConfigurationEditor<>(editor);
  }
}
