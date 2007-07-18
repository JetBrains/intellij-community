package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

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

  private static List<Pair> myIdToNameList = new ArrayList<Pair>();
  static {
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.MAIN_MENU_TITLE));
    myIdToNameList.add(new Pair(IdeActions.GROUP_MAIN_TOOLBAR, ActionsTreeUtil.MAIN_TOOLBAR));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.EDITOR_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.EDITOR_TAB_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.PROJECT_VIEW_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionsTreeUtil.FAVORITES_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_COMMANDER_POPUP, ActionsTreeUtil.COMMANDER_POPUP));
    myIdToNameList.add(new Pair(IdeActions.GROUP_J2EE_VIEW_POPUP, ActionsTreeUtil.J2EE_POPUP));
  }

  @NonNls private static final String GROUP = "group";

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

    for (ActionUrl actionUrl : myActions) {
      final ActionUrl url = new ActionUrl(new ArrayList<String>(actionUrl.getGroupPath()), actionUrl.getComponent(),
                                          actionUrl.getActionType(), actionUrl.getAbsolutePosition());
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
    for (ActionUrl group : myActions) {
      Element groupElement = new Element(GROUP);
      group.writeExternal(groupElement);
      element.addContent(groupElement);
    }
  }

  public AnAction getCorrectedAction(String id) {
    if (! myIdToNameList.contains(new Pair(id, ""))){
      return ActionManager.getInstance().getAction(id);
    }
    if (myIdToActionGroup.get(id) == null) {
      for (Pair pair : myIdToNameList) {
        if (pair.first.equals(id)){
          myIdToActionGroup.put(id, CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance().getAction(id), this, pair.second));
        }
      }
    }
    return myIdToActionGroup.get(id);
  }

  public void resetMainActionGroups() {
    for (Pair pair : myIdToNameList) {
      myIdToActionGroup.put(pair.first, CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance().getAction(pair.first), this, pair.second));
    }
  }

  public static void fillActionGroups(DefaultMutableTreeNode root){
    final ActionManager actionManager = ActionManager.getInstance();
    for (Pair pair : myIdToNameList) {
      final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(pair.first);
      if (actionGroup != null) { //J2EE/Commander plugin was disabled
        root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, pair.second, null, null, true, null)));
      }
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

      for (ActionUrl url : myActions) {
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

  public List<ActionUrl> getChildActions(final ActionUrl url) {
    ArrayList<ActionUrl> result = new ArrayList<ActionUrl>();
    final ArrayList<String> groupPath = url.getGroupPath();
    for (ActionUrl actionUrl : myActions) {
      int index = 0;
      if (groupPath.size() <= actionUrl.getGroupPath().size()){
        while (index < groupPath.size()){
          if (!Comparing.equal(groupPath.get(index), actionUrl.getGroupPath().get(index))){
            break;
          }
          index++;
        }
        if (index == groupPath.size()){
          result.add(actionUrl);
        }
      }
    }
    return result;
  }

  private static class Pair {
    String first;
    String second;

    public Pair(final String first, final String second) {
      this.first = first;
      this.second = second;
    }

    public int hashCode() {
      return first.hashCode();
    }

    public boolean equals(Object obj) {
      return obj instanceof Pair && first.equals(((Pair)obj).first);
    }
  }

}
