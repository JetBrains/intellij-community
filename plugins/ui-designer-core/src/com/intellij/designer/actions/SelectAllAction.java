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

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SelectAllAction extends AnAction {
  protected final EditableArea myArea;

  public SelectAllAction(EditableArea area) {
    super(DesignerBundle.message("action.select.all.text"), DesignerBundle.message("action.select.all.description"), null);
    myArea = area;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }

  public void perform() {
    RadComponent rootComponent = myArea.getRootComponent();
    if (rootComponent != null) {
      final List<RadComponent> components = new ArrayList<>();
      rootComponent.accept(new RadComponentVisitor() {
        @Override
        public void endVisit(RadComponent component) {
          if (!component.isBackground()) {
            components.add(component);
          }
        }
      }, true);
      myArea.setSelection(components);
    }
  }
}