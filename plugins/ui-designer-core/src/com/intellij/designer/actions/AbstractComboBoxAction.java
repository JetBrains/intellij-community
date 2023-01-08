// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractComboBoxAction<T> extends ComboBoxAction {
  protected static final Icon CHECKED = PlatformIcons.CHECK_ICON;

  protected List<T> myItems = Collections.emptyList();
  private T mySelection;
  private Presentation myPresentation;
  private boolean myShowDisabledActions;

  public void setItems(List<T> items, @Nullable T selection) {
    myItems = items;
    setSelection(selection);
  }

  public T getSelection() {
    return mySelection;
  }

  public void setSelection(T selection) {
    mySelection = selection;
    if (selection == null && !myItems.isEmpty()) {
      mySelection = myItems.get(0);
    }
    update();
  }

  public void clearSelection() {
    mySelection = null;
    update();
  }

  public void showDisabledActions(boolean value) {
    myShowDisabledActions = value;
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    myPresentation = presentation;
    update();

    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(
      createComboBoxButton(presentation),
      new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 1, 2, 1), 0, 0));
    return panel;
  }

  @Override
  protected @NotNull ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    if (myShowDisabledActions) {
      return new ComboBoxButton(presentation) {
        @Override
        protected @NotNull JBPopup createPopup(Runnable onDispose) {
          DataContext context = getDataContext();
          ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
            null, createPopupActionGroup(this, context), context, true, onDispose, getMaxRows());
          popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
          return popup;
        }
      };
    }
    return super.createComboBoxButton(presentation);
  }

  public void update() {
    update(mySelection, myPresentation == null ? getTemplatePresentation() : myPresentation, false);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    for (final T item : myItems) {
      if (addSeparator(actionGroup, item)) {
        continue;
      }

      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (mySelection != item && selectionChanged(item)) {
            mySelection = item;
            AbstractComboBoxAction.this.update(item, myPresentation, false);
          }
        }
      };
      actionGroup.add(action);

      Presentation presentation = action.getTemplatePresentation();
      presentation.setIcon(mySelection == item ? CHECKED : null);
      update(item, presentation, true);
    }

    return actionGroup;
  }

  protected boolean addSeparator(DefaultActionGroup actionGroup, T item) {
    return false;
  }

  protected abstract void update(T item, Presentation presentation, boolean popup);

  protected abstract boolean selectionChanged(T item);
}