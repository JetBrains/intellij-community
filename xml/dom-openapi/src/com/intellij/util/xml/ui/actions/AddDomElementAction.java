// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class AddDomElementAction extends AnAction {
 public AddDomElementAction() {
    super(XmlDomBundle.messagePointer("dom.action.add"), AllIcons.General.Add);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!isEnabled(e)) {
      e.getPresentation().setEnabled(false);
      return;
    }

    final AnAction[] actions = getChildren(e);
    for (final AnAction action : actions) {
      e.getPresentation().setEnabled(true);
      action.update(e);
      if (e.getPresentation().isEnabled()) {
        break;
      }
    }
    if (actions.length == 1) {
      e.getPresentation().setText(actions[0].getTemplatePresentation().getText());
    }
    else {
      final String actionText = getActionText(e);
      if (!actionText.endsWith("...")) {
        e.getPresentation().setText(actionText + (actions.length > 1 ? "..." : ""));
      }
    }
    e.getPresentation().setIcon(AllIcons.General.Add);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final AnAction[] actions = getChildren(e);
    if (actions.length > 1) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final AnAction action : actions) {
        group.add(action);
      }

      final DataContext dataContext = e.getDataContext();
      final ListPopup groupPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                            group, dataContext, JBPopupFactory.ActionSelectionAid.NUMBERING, true);

      showPopup(groupPopup, e);
    }
    else if (actions.length == 1) {
      actions[0].actionPerformed(e);
    }
  }

  @NlsActions.ActionText
  protected String getActionText(final AnActionEvent e) {
    return e.getPresentation().getText();
  }

  protected boolean isEnabled(final AnActionEvent e) {
    return true;
  }

  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    final Component component = e.getInputEvent().getComponent();

    if (component instanceof ActionButtonComponent) {
      groupPopup.showUnderneathOf(component);
    }
    else {
      groupPopup.showInBestPositionFor(e.getDataContext());
    }
  }

  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    Project project = e == null ? null : e.getProject();
    if (project == null) return AnAction.EMPTY_ARRAY;

    DomCollectionChildDescription[] descriptions = getDomCollectionChildDescriptions(e);
    final List<AnAction> actions = new ArrayList<>();
    for (DomCollectionChildDescription description : descriptions) {
      final TypeChooser chooser = DomManager.getDomManager(project).getTypeChooserManager().getTypeChooser(description.getType());
      for (Type type : chooser.getChooserTypes()) {

        final Class<?> rawType = ReflectionUtil.getRawType(type);

        String name = TypePresentationService.getService().getTypePresentableName(rawType);
        Icon icon = null;
        if (!showAsPopup() || descriptions.length == 1) {
          //          if (descriptions.length > 1) {
          icon = ElementPresentationManager.getIconForClass(rawType);
          //          }
        }
        actions.add(createAddingAction(e, XmlDomBundle.message("dom.action.add") + " " + name, icon, type, description));
      }
    }
    if (actions.size() > 1 && showAsPopup()) {
      ActionGroup group = new ActionGroup() {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
          return actions.toArray(AnAction.EMPTY_ARRAY);
        }
      };
      return new AnAction[]{new ShowPopupAction(group)};
    }
    else {
      if (actions.size() > 1) {
        actions.add(Separator.getInstance());
      }
    }
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  protected abstract AnAction createAddingAction(final AnActionEvent e,
                                                 final String name,
                                                 final Icon icon,
                                                 final Type type,
                                                 final DomCollectionChildDescription description);

  protected abstract DomCollectionChildDescription @NotNull [] getDomCollectionChildDescriptions(final AnActionEvent e);

  @Nullable
  protected abstract DomElement getParentDomElement(final AnActionEvent e);

  protected abstract JComponent getComponent(AnActionEvent e);

  protected boolean showAsPopup() {
    return true;
  }

  protected class ShowPopupAction extends AnAction {

    protected final ActionGroup myGroup;

    protected ShowPopupAction(ActionGroup group) {
      super(XmlDomBundle.message("dom.action.add"), null, AllIcons.General.Add);
      myGroup = group;
      setShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final ListPopup groupPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                            myGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true);

      showPopup(groupPopup, e);
    }
  }
}
