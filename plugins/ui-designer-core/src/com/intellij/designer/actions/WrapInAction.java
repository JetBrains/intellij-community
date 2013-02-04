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
import com.intellij.designer.model.RadLayout;
import com.intellij.designer.model.WrapInProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.util.ThrowableRunnable;

import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class WrapInAction extends AnAction {
  private final DesignerEditorPanel myDesigner;
  private final EditableArea myArea;
  private final WrapInProvider myProvider;
  private final RadComponent myParent;
  private final List<RadComponent> myComponents;
  private final MetaModel myTarget;

  public WrapInAction(DesignerEditorPanel designer,
                      EditableArea area,
                      WrapInProvider provider,
                      RadComponent parent,
                      List<RadComponent> components,
                      MetaModel target) {
    super(target.getTag(), null, target.getIcon());
    myDesigner = designer;
    myArea = area;
    myProvider = provider;
    myParent = parent;
    myComponents = components;
    myTarget = target;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myArea.select(myProvider.wrapIn(myParent, myComponents, myTarget));
      }
    }, "Run Wrap In action", true);
  }

  public static void fill(DesignerEditorPanel designer, DefaultActionGroup group, EditableArea area) {
    List<RadComponent> selection = area.getSelection();
    if (selection.isEmpty()) {
      return;
    }

    Set<RadComponent> parents = RadComponent.getParents(selection);
    if (parents.size() != 1) {
      return;
    }

    RadComponent parent = parents.iterator().next();
    if (selection.size() > 1) {
      RadLayout layout = parent.getLayout();
      if (layout != null && !layout.isWrapIn(selection)) {
        return;
      }
    }

    WrapInProvider provider = designer.getWrapInProvider();
    if (provider == null) {
      return;
    }

    List<MetaModel> models = provider.getModels();
    if (models.isEmpty()) {
      return;
    }

    DefaultActionGroup wrapGroup = new DefaultActionGroup("Wrap In", true);
    for (MetaModel wrapModel : models) {
      wrapGroup.add(new WrapInAction(designer, area, provider, parent, selection, wrapModel));
    }

    //group.add(wrapGroup); // XXX
  }
}