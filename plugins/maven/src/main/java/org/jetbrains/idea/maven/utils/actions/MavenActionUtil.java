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
package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.*;

public class MavenActionUtil {
  public static Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

  public static MavenProjectsManager getProjectsManager(AnActionEvent e) {
    return MavenProjectsManager.getInstance(getProject(e));
  }

  public static MavenProject getMavenProject(AnActionEvent e) {
    MavenProject result;
    MavenProjectsManager manager = getProjectsManager(e);

    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file != null) {
      result = manager.findProject(file);
      if (result != null) return result;
    }

    Module module = getModule(e);
    if (module != null) {
      result = manager.findProject(module);
      if (result != null) return result;
    }

    return null;
  }

  public static boolean isMavenProject(AnActionEvent e) {
    return getMavenProject(e) != null;
  }

  public static boolean isMavenProjectFile(VirtualFile file) {
    return file != null && !file.isDirectory() && MavenConstants.POM_XML.equals(file.getName());
  }

  public static List<MavenProject> getMavenProjects(AnActionEvent e) {
    Set<MavenProject> result = new LinkedHashSet<MavenProject>();
    for (VirtualFile each : getFiles(e)) {
      MavenProject project = getProjectsManager(e).findProject(each);
      if (project != null) result.add(project);
    }
    if (result.isEmpty()) {
      for (Module each : getModules(e)) {
        MavenProject project = getProjectsManager(e).findProject(each);
        if (project != null) result.add(project);
      }
    }
    return new ArrayList<MavenProject>(result);
  }

  public static List<VirtualFile> getMavenProjectsFiles(AnActionEvent e) {
    return MavenUtil.collectFiles(getMavenProjects(e));
  }

  private static List<VirtualFile> getFiles(AnActionEvent e) {
    VirtualFile[] result = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    return result == null ? Collections.<VirtualFile>emptyList() : Arrays.asList(result);
  }

  private static Module getModule(AnActionEvent e) {
    Module result = e.getData(DataKeys.MODULE);
    return result != null ? result : e.getData(DataKeys.MODULE_CONTEXT);
  }

  private static List<Module> getModules(AnActionEvent e) {
    Module[] result = e.getData(DataKeys.MODULE_CONTEXT_ARRAY);
    if (result != null) return Arrays.asList(result);

    Module module = getModule(e);
    return module != null ? Collections.singletonList(module) : Collections.<Module>emptyList();
  }
}
