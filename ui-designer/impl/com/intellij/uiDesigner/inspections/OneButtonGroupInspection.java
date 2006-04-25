/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;

/**
 * @author yole
 */
public class OneButtonGroupInspection extends BaseFormInspection {
  public OneButtonGroupInspection() {
    super("OneButtonGroup");
  }

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
      collector.addError(getID(), null, UIDesignerBundle.message("inspection.one.button.group.error"), null);
    }
  }
}
