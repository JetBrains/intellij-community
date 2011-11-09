package com.jetbrains.python.console;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;
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

/**
 * @author traff
 */
public class PyConsoleSpecificOptionsPanel {
  private JPanel myWholePanel;
  private JPanel myStartingScriptPanel;
  private JPanel myInterpreterPanel;
  private PyConsoleOptionsProvider.PyConsoleSettings myConsoleSettings;
  private EditorTextField myEditorTextField;
  private AbstractPyCommonOptionsForm myCommonOptionsForm;

  public JPanel createPanel(final Project project, final PyConsoleOptionsProvider.PyConsoleSettings optionsProvider) {
    myInterpreterPanel.setLayout(new BorderLayout());
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(createCommonOptionsFormData(project));

    myInterpreterPanel.add(myCommonOptionsForm.getMainPanel(),
                           BorderLayout.CENTER);
    configureStartingScriptPanel(project, optionsProvider);

    return myWholePanel;
  }

  public void apply() {
    myConsoleSettings.myStartScript = myEditorTextField.getText();
    myConsoleSettings.apply(myCommonOptionsForm);
  }

  public boolean isModified() {
    return !myEditorTextField.getText().equals(myConsoleSettings.myStartScript) || myConsoleSettings.isModified(myCommonOptionsForm);
  }

  public void reset() {
    myEditorTextField.setText(myConsoleSettings.myStartScript);
    myConsoleSettings.reset(myCommonOptionsForm);
  }

  private static PyCommonOptionsFormData createCommonOptionsFormData(final Project project) {
    return new PyCommonOptionsFormData() {
      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public List<Module> getValidModules() {
        return AbstractPythonRunConfiguration.getValidModules(project);
      }
    };
  }

  private void configureStartingScriptPanel(final Project project, final PyConsoleOptionsProvider.PyConsoleSettings optionsProvider) {
    myEditorTextField = new EditorTextField(createDocument(project, optionsProvider.myStartScript), project, PythonFileType.INSTANCE) {
      @Override
      protected EditorEx createEditor() {
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
