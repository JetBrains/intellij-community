package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * User: anna
 * Date: Jan 20, 2005
 */
public class CustomActionsSchema implements JDOMExternalizable {
  public String myName;
  public String myDescription;
  private ArrayList<ActionUrl> myActions = new ArrayList<ActionUrl>();

  private boolean myModified = false;


  private HashMap<String , ActionGroup> myIdToActionGroup = new HashMap<String, ActionGroup>();
  private HashMap<String , String > myIdToName = new HashMap<String, String>();
  {
    myIdToName.put(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.MAIN_MENU_TITLE);
    myIdToName.put(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.MAIN_TOOLBAR);
    myIdToName.put(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.EDITOR_POPUP);
    myIdToName.put(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.EDITOR_TAB_POPUP);
    myIdToName.put(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.FAVORITES_POPUP);
    myIdToName.put(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.PROJECT_VIEW_POPUP);
    myIdToName.put(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.COMMANDER_POPUP);
    myIdToName.put(IdeActions.GROUP_STRUCTURE_VIEW_POPUP, ActionsTreeUtil.STRUCTURE_VIEW_POPUP);
    myIdToName.put(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.J2EE_POPUP);
  }

  private static final String GROUP = "group";

  public CustomActionsSchema() {
  }

  public CustomActionsSchema(String name, String description) {
    myName = name;
    myDescription = description;
  }

  public void addAction(ActionUrl url) {
    myActions.add(url);
  }

  public ArrayList<ActionUrl> getActions() {
    return myActions;
  }

  public void setActions(final ArrayList<ActionUrl> actions) {
    myActions = actions;
  }

  public CustomActionsSchema copyFrom() {
    CustomActionsSchema result = new CustomActionsSchema(myName, myDescription);

    for (Iterator<ActionUrl> iterator = myActions.iterator(); iterator.hasNext();) {
      ActionUrl actionUrl = iterator.next();
      final ActionUrl url =
        new ActionUrl(new ArrayList<String>(actionUrl.getGroupPath()), actionUrl.getComponent(), actionUrl.getActionType(),
                      actionUrl.getAbsolutePosition());
      url.setInitialPosition(actionUrl.getInitialPosition());
      result.addAction(url);
    }

    return result;
  }

  public void setName(final String name) {
    myName = name;
  }

  public void setDescription(final String description) {
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    for (Iterator<Element> iterator = element.getChildren(GROUP).iterator(); iterator.hasNext();) {
      Element groupElement = iterator.next();
      ActionUrl url = new ActionUrl();
      url.readExternal(groupElement);
      myActions.add(url);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeActions(element);
  }

  private void writeActions(Element element) throws WriteExternalException {
    for (Iterator<ActionUrl> iterator = myActions.iterator(); iterator.hasNext();) {
      ActionUrl group = iterator.next();
      Element groupElement = new Element(GROUP);
      group.writeExternal(groupElement);
      element.addContent(groupElement);
    }
  }

  public AnAction getCorrectedAction(String id) {
    if (! myIdToName.containsKey(id)){
      return ActionManager.getInstance().getAction(id);
    }
    if (myIdToActionGroup.get(id) == null) {
      myIdToActionGroup.put(id, CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance().getAction(id), this, myIdToName.get(id)));
    }
    return myIdToActionGroup.get(id);
  }

  public void resetMainActionGroups() {
    for (Iterator<String> iterator = myIdToName.keySet().iterator(); iterator.hasNext();) {
      String id = iterator.next();
      myIdToActionGroup.put(id, CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance().getAction(id), this, myIdToName.get(id)));
    }
  }

  public void fillActionGroups(DefaultMutableTreeNode root){
    final ActionManager actionManager = ActionManager.getInstance();
    for (Iterator<String > iterator = myIdToName.keySet().iterator(); iterator.hasNext();) {
      String name = iterator.next();
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup((ActionGroup)actionManager.getAction(name), myIdToName.get(name), null, null, true)));
    }
  }

  public boolean isModified() {
    return myModified;
  }

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  public boolean isCorrectActionGroup(ActionGroup group) {
    if (myActions.isEmpty()){
      return false;
    }
    if (group.getTemplatePresentation() != null &&
        group.getTemplatePresentation().getText() != null) {

      final String text = group.getTemplatePresentation().getText();

      for (Iterator<ActionUrl> iterator = myActions.iterator(); iterator.hasNext();) {
        ActionUrl url = iterator.next();
        if (url.getGroupPath().contains(text)) {
          return true;
        }
        if (url.getComponent() instanceof Group) {
          final Group urlGroup = (Group)url.getComponent();
          String id = urlGroup.getName() != null ? urlGroup.getName() : urlGroup.getId();
          if (id == null || id.equals(text)) {
            return true;
          }
        }
      }
      return false;
    }
    return true;
  }

}
