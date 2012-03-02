/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractComboBoxAction<T> extends ComboBoxAction {
  private List<T> myItems = Collections.emptyList();
  private T mySelection;
  private Presentation myPresentation;

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
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    myPresentation = presentation;
    update(mySelection, presentation);
    return super.createCustomComponent(presentation);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    for (final T item : myItems) {
      if (addSeparator(actionGroup, item)) {
        continue;
      }

      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (selectionChanged(item)) {
            mySelection = item;
            AbstractComboBoxAction.this.update(item, myPresentation);
          }
        }
      };
      update(item, action.getTemplatePresentation());
      actionGroup.add(action);
    }

    return actionGroup;
  }

  protected boolean addSeparator(DefaultActionGroup actionGroup, T item) {
    return false;
  }

  protected abstract void update(T item, Presentation presentation);

  protected abstract boolean selectionChanged(T item);
}