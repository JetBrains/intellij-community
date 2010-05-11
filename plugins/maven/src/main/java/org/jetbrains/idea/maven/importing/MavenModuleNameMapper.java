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
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.util.*;

public class MavenModuleNameMapper {
  public static void map(Collection<MavenProject> projects,
                         Map<MavenProject, Module> mavenProjectToModule,
                         Map<MavenProject, String> mavenProjectToModuleName,
                         Map<MavenProject, String> mavenProjectToModulePath,
                         String dedicatedModuleDir) {
    resolveModuleNames(projects,
                       mavenProjectToModule,
                       mavenProjectToModuleName);
    resolveModulePaths(projects,
                       mavenProjectToModule,
                       mavenProjectToModuleName,
                       mavenProjectToModulePath,
                       dedicatedModuleDir);
  }

  private static void resolveModuleNames(Collection<MavenProject> projects,
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName) {
    List<NameItem> names = new ArrayList<NameItem>();

    for (MavenProject each : projects) {
      names.add(new NameItem(each, mavenProjectToModule.get(each)));
    }

    Collections.sort(names, new Comparator<NameItem>() {
      public int compare(NameItem o1, NameItem o2) {
        return o1.project.getPath().compareToIgnoreCase(o2.project.getPath());
      }
    });

    for (NameItem each : names) {
      if (each.hasDuplicatedGroup) continue;

      String name = each.getResultName();
      for (NameItem other : names) {
        if (each == other) continue;
        if (name.equals(other.getResultName()) && each.groupId.equals(other.groupId)) {
          each.setHasDuplicatedGroup(true);
          other.setHasDuplicatedGroup(true);
        }
      }
    }

    for (NameItem each : names) {
      int count = each.number;
      if (count != -1) continue;

      count = 0;
      String name = each.getResultName();
      for (NameItem other : names) {
        if (each == other) continue;
        if (name.equals(other.getResultName())) {
          other.setNumber(++count);
        }
      }
      if (count > 0) each.setNumber(0);
    }

    for (NameItem each : names) {
      mavenProjectToModuleName.put(each.project, each.getResultName());
    }
  }

  public static class NameItem {
    public final MavenProject project;
    public final Module module;

    public final String originalName;
    public final String groupId;

    public int number = -1; // has no duplicates
    public boolean hasDuplicatedGroup;

    public NameItem(MavenProject project, Module module) {
      this.project = project;
      this.module = module;
      originalName = calcOriginalName();

      String group = project.getMavenId().getGroupId();
      groupId = isValidName(group) ? group : "";
    }

    private String calcOriginalName() {
      if (module != null) return module.getName();

      String name = project.getMavenId().getArtifactId();
      if (!isValidName(name)) name = project.getDirectoryFile().getName();
      return name;
    }

    public void setNumber(int num) {
      number = num;
    }

    public void setHasDuplicatedGroup(boolean value) {
      hasDuplicatedGroup = value;
    }

    public String getResultName() {
      if (module != null) return module.getName();

      if (number == -1) return originalName;
      String result = originalName + " (" + (number + 1) + ")";
      if (!hasDuplicatedGroup && groupId.length() != 0) {
        result += " (" + groupId + ")";
      }
      return result;
    }
  }

  private static boolean isValidName(String name) {
    if (StringUtil.isEmptyOrSpaces(name)) return false;
    if (name.equals(MavenId.UNKNOWN_VALUE)) return false;

    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (!(Character.isDigit(ch) || Character.isLetter(ch) || ch == '-' || ch == '_' || ch == '.')) {
        return false;
      }
    }
    return true;
  }

  private static void resolveModulePaths(Collection<MavenProject> projects,
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName,
                                         Map<MavenProject, String> mavenProjectToModulePath,
                                         String dedicatedModuleDir) {
    for (MavenProject each : projects) {
      Module module = mavenProjectToModule.get(each);
      String path = module != null
                    ? module.getModuleFilePath()
                    : generateModulePath(each,
                                         mavenProjectToModuleName,
                                         dedicatedModuleDir);
      mavenProjectToModulePath.put(each, path);
    }
  }

  private static String generateModulePath(MavenProject project,
                                           Map<MavenProject, String> mavenProjectToModuleName,
                                           String dedicatedModuleDir) {
    String dir = StringUtil.isEmptyOrSpaces(dedicatedModuleDir)
                 ? project.getDirectory()
                 : dedicatedModuleDir;
    String fileName = mavenProjectToModuleName.get(project) + ModuleFileType.DOT_DEFAULT_EXTENSION;
    return new File(dir, fileName).getPath();
  }
}
