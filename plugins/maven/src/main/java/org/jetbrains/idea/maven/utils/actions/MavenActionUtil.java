/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.*;

public class MavenActionUtil {
  private MavenActionUtil() {
  }

  public static boolean hasProject(DataContext context) {
    return PlatformDataKeys.PROJECT.getData(context) != null;
  }
  
  public static Project getProject(DataContext context) {
    return PlatformDataKeys.PROJECT.getData(context);
  }

  public static MavenProject getMavenProject(DataContext context) {
    MavenProject result;
    final MavenProjectsManager manager = getProjectsManager(context);

    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(context);
    if (file != null) {
      result = manager.findProject(file);
      if (result != null) return result;
    }

    Module module = getModule(context);
    if (module != null) {
      result = manager.findProject(module);
      if (result != null) return result;
    }

    return null;
  }

  @Nullable
  private static Module getModule(DataContext context) {
    final Module module = DataKeys.MODULE.getData(context);
    return module != null ? module : DataKeys.MODULE_CONTEXT.getData(context);
  }

  public static MavenProjectsManager getProjectsManager(DataContext context) {
    return MavenProjectsManager.getInstance(getProject(context));
  }

  public static boolean isMavenProjectFile(VirtualFile file) {
    return file != null && !file.isDirectory() && MavenConstants.POM_XML.equals(file.getName());
  }

  public static List<MavenProject> getMavenProjects(DataContext context) {
    Set<MavenProject> result = new LinkedHashSet<MavenProject>();
    for (VirtualFile each : getFiles(context)) {
      MavenProject project = getProjectsManager(context).findProject(each);
      if (project != null) result.add(project);
    }
    if (result.isEmpty()) {
      for (Module each : getModules(context)) {
        MavenProject project = getProjectsManager(context).findProject(each);
        if (project != null) result.add(project);
      }
    }
    return new ArrayList<MavenProject>(result);
  }

  public static List<VirtualFile> getMavenProjectsFiles(DataContext context) {
    return MavenUtil.collectFiles(getMavenProjects(context));
  }

  private static List<VirtualFile> getFiles(DataContext context) {
    VirtualFile[] result = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    return result == null ? Collections.<VirtualFile>emptyList() : Arrays.asList(result);
  }

  private static List<Module> getModules(DataContext context) {
    Module[] result = DataKeys.MODULE_CONTEXT_ARRAY.getData(context);
    if (result != null) return Arrays.asList(result);

    Module module = getModule(context);
    return module != null ? Collections.singletonList(module) : Collections.<Module>emptyList();
  }
}
