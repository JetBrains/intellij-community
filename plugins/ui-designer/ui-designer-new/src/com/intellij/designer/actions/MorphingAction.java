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

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.util.ThrowableRunnable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class MorphingAction extends AnAction {
  private final DesignerEditorPanel myDesigner;
  private final List<RadComponent> myComponents;
  private final MetaModel myTarget;

  public MorphingAction(DesignerEditorPanel designer, List<RadComponent> components, MetaModel target) {
    super(target.getTag(), null, target.getIcon());
    myDesigner = designer;
    myComponents = components;
    myTarget = target;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        for (RadComponent component : myComponents) {
          component.morphingTo(myTarget);
        }
      }
    }, "Run Morphing action", true);
  }

  public static void fill(DesignerEditorPanel designer, DefaultActionGroup group, EditableArea area) {
    List<RadComponent> selection = area.getSelection();
    if (selection.isEmpty()) {
      return;
    }

    MetaModel model = null;
    for (RadComponent component : selection) {
      if (model == null) {
        model = component.getMetaModel();
      }
      else if (model != component.getMetaModel()) {
        return;
      }
    }
    if (model == null) {
      return;
    }

    List<MetaModel> models = model.getMorphingModels();
    if (models.isEmpty()) {
      return;
    }

    DefaultActionGroup morphingGroup = new DefaultActionGroup("Morphing", true);
    for (MetaModel morphingModel : models) {
      morphingGroup.add(new MorphingAction(designer, selection, morphingModel));
    }

    group.add(morphingGroup);
  }
}