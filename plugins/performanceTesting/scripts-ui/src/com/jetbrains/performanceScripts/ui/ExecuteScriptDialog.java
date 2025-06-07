// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.ui;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.jetbrains.performancePlugin.utils.ScriptRunner;
import com.jetbrains.performanceScripts.PerformanceScriptsBundle;
import com.jetbrains.performanceScripts.lang.IJPerfFileType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public final class ExecuteScriptDialog extends DialogWrapper {
  private final Project project;
  private EditorTextField scriptText;
  private JPanel myMainPanel;
  private ComboBox<Scenario> scenarioComboBox;
  private static String lastUsedText = null;
  private final ScriptRunner myScriptRunner;
  private final Action execute;

  public ExecuteScriptDialog(final @NotNull Project project) {
    super(project);
    this.project = project;
    myScriptRunner = new ScriptRunner();
    execute = createExecuteAction();
    setTitle(PerformanceScriptsBundle.message("executor.title"));
    scenarioComboBox.addItem(new Scenario(PerformanceScriptsBundle.message("typing.scenario"), generateTestScript(PerformanceScriptType.TYPING)));
    scenarioComboBox.addItem(new Scenario(PerformanceScriptsBundle.message("formatting.scenario"), generateTestScript(PerformanceScriptType.FORMATTING)));
    scenarioComboBox.addItem(new Scenario(PerformanceScriptsBundle.message("local.inspection.scenario"), generateTestScript(PerformanceScriptType.INSPECTION)));
    scenarioComboBox.setEditable(false);
    scenarioComboBox.setSelectedIndex(0);
    scenarioComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        Scenario item = (Scenario)e.getItem();
        scriptText.setText(getCurrentFileCommand(project) + item.getScript());
      }
    });

    init();
  }

  private void createUIComponents() {
    scriptText = new EditorTextField(project, IJPerfFileType.INSTANCE);
    scriptText.setOneLineMode(false);
    scriptText.setText(lastUsedText != null ? lastUsedText :
                       getCurrentFileCommand(project) + generateTestScript(PerformanceScriptType.TYPING));
  }

  private static String getCurrentFileCommand(Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    String openFileCommand = "";
    if (fileEditorManager != null) {
      VirtualFile[] openFiles = fileEditorManager.getSelectedFiles();
      if (openFiles.length != 0) {
        openFileCommand = "%openFile " + openFiles[0].getCanonicalPath() + '\n';
      }
    }
    return openFileCommand;
  }

  private enum PerformanceScriptType {
    TYPING,
    FORMATTING,
    INSPECTION
  }

  @Contract(pure = true)
  private static @NotNull String generateTestScript(@NotNull PerformanceScriptType scriptType) {
    return switch (scriptType) {
      case TYPING -> """
        %delayType 150|Sample text for typing scenario
        %pressKey ENTER
        %delayType 150|Sample text for typing scenario
        %pressKey ENTER
        %delayType 150|Sample text for typing scenario""";
      case FORMATTING -> "%reformat";
      case INSPECTION -> """
        %doLocalInspection
        %doLocalInspection
        %doLocalInspection""";
    };
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{execute, getCancelAction()};
  }

  private Action createExecuteAction() {
    return new DialogWrapperAction(PerformanceScriptsBundle.message("execute.script.button")) {
      @Override
      protected void doAction(ActionEvent e) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        lastUsedText = scriptText.getText();
        doOKAction();
        myScriptRunner.doRunScript(ExecuteScriptDialog.this.project,
                                   ExecuteScriptDialog.this.scriptText.getText(),
                                   null);
      }
    };
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return scriptText;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myMainPanel;
  }

  static class Scenario {
    private final String myName;
    private final String myScript;

    Scenario(String name, String script) {
      myName = name;
      myScript = script;
    }

    @Override
    public String toString() {
      return myName;
    }

    public String getScript() {
      return myScript;
    }
  }
}
