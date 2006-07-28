package com.intellij.tools;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Belyaev
 */
public class ExternalToolsGroup extends SimpleActionGroup {
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    removeAll();
    String context = event.getPlace();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
    String[] groups = ToolManager.getInstance().getGroups();
    for (String groupName : groups) {
      if (groupName != null && groupName.trim().length() > 0) {
        SimpleActionGroup subgroup = new SimpleActionGroup();
        subgroup.getTemplatePresentation().setText(groupName, false);
        subgroup.setPopup(true);
        fillGroup(context, groupName, subgroup);
        if (subgroup.getChildrenCount() > 0) {
          add(subgroup);
        }
      }
      else {
        fillGroup(context, null, this);
      }
    }
    presentation.setVisible(getChildrenCount() > 0);
  }

  private static void fillGroup(String context, String groupName, SimpleActionGroup group) {
    Tool[] tools = ToolManager.getInstance().getTools(groupName);
    for (Tool tool : tools) {
      if (isToolVisible(tool, context)) {
        addToolToGroup(tool, group);
      }
    }
  }

  private static void addToolToGroup(Tool tool, SimpleActionGroup group) {
    String id = tool.getActionId();
    AnAction action = ActionManager.getInstance().getAction(id);
    if (action == null) {
      action = new ToolAction(tool);
    }

    group.add(action);
  }

  private static boolean isToolVisible(Tool tool, String context) {
    if (!tool.isEnabled()) return false;
    if (ActionPlaces.EDITOR_POPUP.equals(context)) {
      return tool.isShownInEditor();
    }
    else if (
      ActionPlaces.PROJECT_VIEW_POPUP.equals(context) ||
      ActionPlaces.COMMANDER_POPUP.equals(context) ||
      ActionPlaces.J2EE_VIEW_POPUP.equals(context) ||
      ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.CALL_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP.equals(context) ||
      ActionPlaces.FAVORITES_VIEW_POPUP.equals(context) ||
      ActionPlaces.SCOPE_VIEW_POPUP.equals(context) ||
      ActionPlaces.NAVIGATION_BAR.equals(context)
    ){
      return tool.isShownInProjectViews();
    }
    else if (ActionPlaces.MAIN_MENU.equals(context)) {
      return tool.isShownInMainMenu();
    }
    else if (ActionPlaces.USAGE_VIEW_POPUP.equals(context)) {
      return tool.isShownInSearchResultsPopup();
    }
    return false;
  }

}