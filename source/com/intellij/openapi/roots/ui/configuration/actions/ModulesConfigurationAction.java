package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.wm.ex.ActionToolbarEx;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 25
 * @author 2003
 */
public abstract class ModulesConfigurationAction extends AnAction implements CustomComponentAction {
  protected ModulesConfigurationAction() {
  }

  protected ModulesConfigurationAction(String text) {
    super(text);
  }

  protected ModulesConfigurationAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public JComponent createCustomComponent(final Presentation presentation) {
    return createCustomComponentImpl(this, presentation);
  }

  public static JComponent createCustomComponentImpl(final AnAction action, final Presentation presentation) {
    return new ActionButtonWithText(action, presentation, ActionPlaces.UNKNOWN, ActionToolbarEx.DEFAULT_MINIMUM_BUTTON_SIZE);
  }
}
