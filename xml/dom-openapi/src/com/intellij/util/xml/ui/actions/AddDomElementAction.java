/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.TypeChooser;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.DomCollectionControl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public abstract class AddDomElementAction extends AnAction {

  public AddDomElementAction() {
    super(ApplicationBundle.message("action.add"), null, DomCollectionControl.ADD_ICON);
  }

  @Override
  public void update(AnActionEvent e) {
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
    } else {
      final String actionText = getActionText(e);
      if (!actionText.endsWith("...")) {
        e.getPresentation().setText(actionText + (actions.length > 1 ? "..." : ""));
      }
    }
    e.getPresentation().setIcon(DomCollectionControl.ADD_ICON);

    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
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
    } else {
      groupPopup.showInBestPositionFor(e.getDataContext());
    }
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
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
        actions.add(createAddingAction(e, ApplicationBundle.message("action.add") + " " + name, icon, type, description));
      }
    }
    if (actions.size() > 1 && showAsPopup()) {
      ActionGroup group = new ActionGroup() {
        @Override
        @NotNull
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
          return actions.toArray(new AnAction[actions.size()]);
        }
      };
      return new AnAction[]{new ShowPopupAction(group)};
    }
    else {
      if (actions.size() > 1) {
        actions.add(Separator.getInstance());
      } else if (actions.size() == 1) {

      }
    }
    return actions.toArray(new AnAction[actions.size()]);
  }

  protected abstract AnAction createAddingAction(final AnActionEvent e,
                                                 final String name,
                                                 final Icon icon,
                                                 final Type type,
                                                 final DomCollectionChildDescription description);

  @NotNull
  protected abstract DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e);

  @Nullable
  protected abstract DomElement getParentDomElement(final AnActionEvent e);

  protected abstract JComponent getComponent(AnActionEvent e);

  protected boolean showAsPopup() {
    return true;
  }

  protected class ShowPopupAction extends AnAction {

    protected final ActionGroup myGroup;

    protected ShowPopupAction(ActionGroup group) {
      super(ApplicationBundle.message("action.add"), null, DomCollectionControl.ADD_ICON);
      myGroup = group;
      setShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ListPopup groupPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                            myGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true);

      showPopup(groupPopup, e);
    }
  }
}
