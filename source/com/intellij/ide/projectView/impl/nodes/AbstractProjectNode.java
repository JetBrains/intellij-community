/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;

import java.util.*;

public abstract class AbstractProjectNode extends ProjectViewNode<Project> {
  public AbstractProjectNode(Project project, Project value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public abstract Collection<AbstractTreeNode> getChildren();

  protected Collection<AbstractTreeNode> modulesAndGroups(Module[] modules) {
    Map<String, List<Module>> groups = new HashMap<String, List<Module>>();
    List<Module> nonGroupedModules = new ArrayList<Module>(Arrays.asList(modules));
    for (int i = 0; i < modules.length; i++) {
      final Module module = modules[i];
      String group = ModuleManager.getInstance(getProject()).getModuleGroup(module);
      if (group != null) {
        List<Module> moduleList = groups.get(group);
        if (moduleList == null) {
          moduleList = new ArrayList<Module>();
          groups.put(group, moduleList);
        }
        moduleList.add(module);
        nonGroupedModules.remove(module);
      }
    }
    String[] groupArray = groups.keySet().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (int j = 0; j < groupArray.length; j++) {
      final String name = groupArray[j];
      result.add(new ModuleGroupNode(getProject(), new ModuleGroup(name), getSettings(), getModuleNodeClass()));
    }
    result.addAll(ProjectViewNode.wrap(nonGroupedModules, getProject(), getModuleNodeClass(), getSettings()));
    return result;
  }

  protected abstract Class<? extends AbstractTreeNode> getModuleNodeClass();

  public void update(PresentationData presentation) {
    presentation.setIcons(Icons.PROJECT_ICON);
    final VirtualFile projectFile = getProject().getProjectFile();
    presentation.setPresentableText(projectFile != null ? projectFile.getName() : "");
  }

  public String getTestPresentation() {
    return "Project";
  }

  public boolean contains(VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    return index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file);
  }
}
