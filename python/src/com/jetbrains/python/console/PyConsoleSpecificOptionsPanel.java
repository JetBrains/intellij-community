// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PyConsoleSpecificOptionsPanel {
  private final Project myProject;
  private JPanel myWholePanel;
  private JPanel myStartingScriptPanel;
  private JPanel myInterpreterPanel;
  private PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private EditorTextField myEditorTextField;
  private AbstractPyCommonOptionsForm myCommonOptionsForm;

  public PyConsoleSpecificOptionsPanel(Project project) {
    myProject = project;
  }

  public JPanel createPanel(final PyConsoleOptions.PyConsoleSettings optionsProvider) {
    myInterpreterPanel.setLayout(new BorderLayout());
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(createCommonOptionsFormData());
    myCommonOptionsForm.subscribe();

    myInterpreterPanel.add(myCommonOptionsForm.getMainPanel(),
                           BorderLayout.CENTER);

    configureStartingScriptPanel(optionsProvider);

    return myWholePanel;
  }

  public void apply() {
    myConsoleSettings.myCustomStartScript = myEditorTextField.getText();
    myConsoleSettings.apply(myCommonOptionsForm);
  }

  public boolean isModified() {
    return !myEditorTextField.getText().equals(myConsoleSettings.myCustomStartScript) || myConsoleSettings.isModified(myCommonOptionsForm);
  }

  public void reset() {
    UIUtil.invokeLaterIfNeeded(() -> myEditorTextField.setText(myConsoleSettings.myCustomStartScript));

    myConsoleSettings.reset(myProject, myCommonOptionsForm);
  }

  private PyCommonOptionsFormData createCommonOptionsFormData() {
    return new PyCommonOptionsFormData() {
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
        return true;
      }
    };
  }

  private void configureStartingScriptPanel(final PyConsoleOptions.PyConsoleSettings optionsProvider) {
    myEditorTextField =
      new EditorTextField(createDocument(myProject, optionsProvider.myCustomStartScript), myProject, PythonFileType.INSTANCE) {
        @Override
        protected @NotNull EditorEx createEditor() {
          final EditorEx editor = super.createEditor();
          editor.setVerticalScrollbarVisible(true);
          return editor;
        }

        @Override
        protected boolean isOneLineMode() {
          return false;
        }
      };
    myStartingScriptPanel.setLayout(new BorderLayout());
    myStartingScriptPanel.add(myEditorTextField, BorderLayout.CENTER);
    myConsoleSettings = optionsProvider;
  }

  @NotNull
  public static Document createDocument(@NotNull final Project project,
                                        @NotNull String text) {
    text = text.trim();
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "start_script.py", text, true);

    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }
}
