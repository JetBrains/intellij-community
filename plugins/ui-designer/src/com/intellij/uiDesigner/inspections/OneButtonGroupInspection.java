/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OneButtonGroupInspection extends BaseFormInspection {
  public OneButtonGroupInspection() {
    super("OneButtonGroup");
  }

  @NotNull
  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.one.button.group");
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    final IRootContainer root = FormEditingUtil.getRoot(component);
    if (root == null) return;
    String groupName = root.getButtonGroupName(component);
    if (groupName != null) {
      final String[] sameGroupComponents = root.getButtonGroupComponentIds(groupName);
      for(String id: sameGroupComponents) {
        final IComponent otherComponent = FormEditingUtil.findComponent(root, id);
        if (otherComponent != null && otherComponent != component) {
          return;
        }
      }
      collector.addError(getID(), component, null, UIDesignerBundle.message("inspection.one.button.group.error"));
    }
  }
}
