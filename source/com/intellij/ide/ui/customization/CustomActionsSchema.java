package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.ArrayList;
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


  private ActionGroup myMainMenuActionGroup;
  private ActionGroup myMainToolabarActionGroup;
  private ActionGroup myEditorPopupActionGroup;

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

  public ActionGroup getMainMenuActionGroup() {
    if (myMainMenuActionGroup == null) {
      myMainMenuActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                     .getAction(IdeActions.GROUP_MAIN_MENU), this,
                                                                               ActionsTreeUtil.MAIN_MENU_TITLE);
    }
    return myMainMenuActionGroup;
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

  public ActionGroup getMainToolbarActionsGroup() {
    if (myMainToolabarActionGroup == null) {
      myMainToolabarActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                         .getAction(IdeActions.GROUP_MAIN_TOOLBAR), this,
                                                                                   ActionsTreeUtil.MAIN_TOOLBAR);
    }
    return myMainToolabarActionGroup;
  }

  public ActionGroup getEditorPopupGroup() {
    if (myEditorPopupActionGroup == null) {
      myEditorPopupActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                        .getAction(IdeActions.GROUP_EDITOR_POPUP), this,
                                                                                  ActionsTreeUtil.EDITOR_POPUP);
    }
    return myEditorPopupActionGroup;
  }

  public void resetMainActionGroups() {
    myMainMenuActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                   .getAction(IdeActions.GROUP_MAIN_MENU), this,
                                                                             ActionsTreeUtil.MAIN_MENU_TITLE);
    myMainToolabarActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                       .getAction(IdeActions.GROUP_MAIN_TOOLBAR), this,
                                                                                 ActionsTreeUtil.MAIN_TOOLBAR);
    myEditorPopupActionGroup = CustomizationUtil.correctActionGroup((ActionGroup)ActionManager.getInstance()
                                                                      .getAction(IdeActions.GROUP_EDITOR_POPUP), this,
                                                                                ActionsTreeUtil.EDITOR_POPUP);
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
