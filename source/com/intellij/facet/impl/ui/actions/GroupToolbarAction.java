/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopupStep;

import javax.swing.*;

/**
 * @author nik
 */
public class GroupToolbarAction extends AnAction {
  private ActionGroup myGroup;
  private JComponent myToolbarComponent;

  public GroupToolbarAction(final ActionGroup group, JComponent toolbarComponent) {
    super(group.getTemplatePresentation().getText(), group.getTemplatePresentation().getDescription(),
          group.getTemplatePresentation().getIcon());
    myGroup = group;
    myToolbarComponent = toolbarComponent;
  }

  public void actionPerformed(AnActionEvent e) {
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final ListPopupStep popupStep = popupFactory.createActionsStep(myGroup, e.getDataContext(), false, false,
                                                                   myGroup.getTemplatePresentation().getText(), myToolbarComponent, false,
                                                                   0);
    popupFactory.createListPopup(popupStep).showUnderneathOf(myToolbarComponent);
  }

  public void update(AnActionEvent e) {
    myGroup.update(e);
  }
}
