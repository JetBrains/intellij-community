package com.jetbrains.performancePlugin.ui;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.lang.IJPerfFileType;
import com.jetbrains.performancePlugin.utils.ScriptRunner;
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

  public ExecuteScriptDialog(@NotNull final Project project) {
    super(project);
    this.project = project;
    myScriptRunner = new ScriptRunner();
    execute = createExecuteAction();
    setTitle(PerformanceTestingBundle.message("executor.title"));
    scenarioComboBox.addItem(new Scenario(PerformanceTestingBundle.message("typing.scenario"), generateTestScript(PerformanceScriptType.TYPING)));
    scenarioComboBox.addItem(new Scenario(PerformanceTestingBundle.message("formatting.scenario"), generateTestScript(PerformanceScriptType.FORMATTING)));
    scenarioComboBox.addItem(new Scenario(PerformanceTestingBundle.message("local.inspection.scenario"), generateTestScript(PerformanceScriptType.INSPECTION)));
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

  @NotNull
  @Contract(pure = true)
  private static String generateTestScript(@NotNull PerformanceScriptType scriptType) {
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
    return new DialogWrapperAction(PerformanceTestingBundle.message("execute.script.button")) {
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

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return scriptText;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
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
