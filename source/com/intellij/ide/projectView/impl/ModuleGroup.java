/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;

import java.util.*;

public class ModuleGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.ModuleGroup");
  private final String[] myGroupPath;

  public ModuleGroup(String[] groupPath) {
    LOG.assertTrue(groupPath != null);
    myGroupPath = groupPath;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleGroup)) return false;

    final ModuleGroup moduleGroup = (ModuleGroup)o;

    if (!Arrays.equals(myGroupPath, moduleGroup.myGroupPath)) return false;

    return true;
  }

  public int hashCode() {
    return myGroupPath[myGroupPath.length-1].hashCode();
  }

  public String[] getGroupPath() {
    return myGroupPath;
  }

  public Module[] modulesInGroup(Project project, boolean recursively) {
    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    List<Module> result = new ArrayList<Module>();
    for (final Module module : allModules) {
      String[] group = ModuleManager.getInstance(project).getModuleGroupPath(module);
      if (group == null) continue;
      if (Arrays.equals(myGroupPath, group) || (recursively && isChild(myGroupPath, group))) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }

  public Collection<ModuleGroup> childGroups(Project project) {
    final Module[] allModules = ModuleManager.getInstance(project).getModules();

    Set<ModuleGroup> result = new THashSet<ModuleGroup>();
    for (Module module : allModules) {
      String[] group = ModuleManager.getInstance(project).getModuleGroupPath(module);
      if (group == null) continue;
      final String[] directChild = directChild(myGroupPath, group);
      if (directChild != null) {
        result.add(new ModuleGroup(directChild));
      }
    }

    return result;
  }

  private static boolean isChild(final String[] parent, final String[] descendant) {
    if (parent.length >= descendant.length) return false;
    for (int i = 0; i < parent.length; i++) {
      String group = parent[i];
      if (!group.equals(descendant[i])) return false;
    }
    return true;
  }

  private static String[] directChild(final String[] parent, final String[] descendant) {
    if (!isChild(parent, descendant)) return null;
    return ArrayUtil.append(parent, descendant[parent.length]);
  }

  public String presentableText() {
    return "'" + myGroupPath[myGroupPath.length - 1] + "'";
  }

  public String toString() {
    return myGroupPath[myGroupPath.length - 1];
  }
}