package com.intellij.tools;

import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author Eugene Belyaev
 */
public class ToolAction extends AnAction {
  private final String myActionId;

  public ToolAction(Tool tool) {
    myActionId = tool.getActionId();
    getTemplatePresentation().setText(tool.getName(), false);
    getTemplatePresentation().setDescription(tool.getDescription());
  }

  public void actionPerformed(AnActionEvent e) {
    MacroManager.getInstance().cacheMacrosPreview(e.getDataContext());
    Tool[] tools = ToolManager.getInstance().getTools();
    for (int i = 0; i < tools.length; i++) {
      Tool tool = tools[i];
      if (myActionId.equals(tool.getActionId())) {
        tool.execute(e.getDataContext());
        break;
      }
    }
  }
}
