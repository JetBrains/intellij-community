package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;


public class ModuleGroupUrl extends AbstractUrl {
  @NonNls private static final String ELEMENT_TYPE = "module_group";

  public ModuleGroupUrl(String url) {
    super(url, null, ELEMENT_TYPE);
  }

  public Object[] createPath(Project project) {
    final String[] groupPath = url.split(";");
    return new Object[]{new ModuleGroup(groupPath)};
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
      return new ModuleGroupUrl(url);
  }

  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof ModuleGroup) {
      ModuleGroup group = (ModuleGroup)element;
      final String[] groupPath = group.getGroupPath();
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < groupPath.length; i++) {
        String s = groupPath[i];
        sb.append(s);
        sb.append(";");
      }
      return new ModuleGroupUrl(sb.toString());
    }
    return null;
  }
}
