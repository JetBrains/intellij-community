/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.ArrayList;

public class ModuleGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.ModuleGroup");
  private final String myName;

  public ModuleGroup(String name) {
    LOG.assertTrue(name != null);
    myName = name;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleGroup)) return false;

    final ModuleGroup moduleGroup = (ModuleGroup)o;

    if (!myName.equals(moduleGroup.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public String getName() {
    return myName;
  }

  public Module[] modulesInGroup(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    List<Module> result = new ArrayList<Module>();
    for (int i = 0; i < modules.length; i++) {
      final Module module = modules[i];
      String group = ModuleManager.getInstance(project).getModuleGroup(module);
      if (myName.equals(group)) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }
}