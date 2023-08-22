// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import org.jetbrains.annotations.NotNull;


public class OneButtonGroupInspection extends BaseFormInspection {
  public OneButtonGroupInspection() {
    super("OneButtonGroup");
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
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
