package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class StartStopMacroRecordingAction extends AnAction {
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getDataContext().getData(DataConstants.EDITOR) != null);
    e.getPresentation().setText(ActionMacroManager.getInstance().isRecording() ? "Stop Macro Recording" : "Start Macro Recording");
  }

  public void actionPerformed(AnActionEvent e) {
    if (!ActionMacroManager.getInstance().isRecording() ) {
      final ActionMacroManager manager = ActionMacroManager.getInstance();
      manager.startRecording("<noname>");
    }
    else {
      ActionMacroManager.getInstance().stopRecording((Project) e.getDataContext().getData(DataConstants.PROJECT));
    }
  }
}
