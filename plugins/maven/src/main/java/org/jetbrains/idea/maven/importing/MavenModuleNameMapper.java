// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.util.*;

public final class MavenModuleNameMapper {
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

  public static String resolveModuleName(MavenProject project) {
    return new NameItem(project, null).getResultName();
  }

  private static void resolveModuleNames(Collection<MavenProject> projects,
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName) {
    NameItem[] names = new NameItem[projects.size()];

    int i = 0;
    for (MavenProject each : projects) {
      names[i++] = new NameItem(each, mavenProjectToModule.get(each));
    }

    Arrays.sort(names);

    Map<String, Integer> nameCounters = new HashMap<>();

    for (i = 0; i < names.length; i++) {
      if (names[i].hasDuplicatedGroup) continue;

      for (int k = i + 1; k < names.length; k++) {
        if (names[i].originalName.equals(names[k].originalName)) {
          nameCounters.put(names[i].originalName, 0);

          if (names[i].groupId.equals(names[k].groupId)) {
            names[i].hasDuplicatedGroup = true;
            names[k].hasDuplicatedGroup = true;
          }
        }
      }
    }

    Set<String> existingNames = new HashSet<>();

    for (NameItem name : names) {
      if (name.module != null) {
        boolean wasAdded = existingNames.add(name.getResultName());
        //assert wasAdded : name.getResultName();
      }
    }

    for (NameItem nameItem : names) {
      if (nameItem.module == null) {

        Integer c = nameCounters.get(nameItem.originalName);

        if (c != null) {
          nameItem.number = c;
          nameCounters.put(nameItem.originalName, c + 1);
        }

        do {
          String name = nameItem.getResultName();
          if (existingNames.add(name)) break;

          nameItem.number++;
          nameCounters.put(nameItem.originalName, nameItem.number + 1);
        }
        while (true);
      }
    }

    for (NameItem each : names) {
      mavenProjectToModuleName.put(each.project, each.getResultName());
    }

    //assert new HashSet<String>(mavenProjectToModuleName.values()).size() == mavenProjectToModuleName.size() : new HashMap<MavenProject, String>(mavenProjectToModuleName);
  }

  private static class NameItem implements Comparable<NameItem> {
    public final MavenProject project;
    public final Module module;

    public final String originalName;
    public final String groupId;

    public int number = -1; // has no duplicates
    public boolean hasDuplicatedGroup;

    private NameItem(MavenProject project, @Nullable Module module) {
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

    public String getResultName() {
      if (module != null) return module.getName();

      if (number == -1) return originalName;
      String result = originalName + " (" + (number + 1) + ")";
      if (!hasDuplicatedGroup && groupId.length() != 0) {
        result += " (" + groupId + ")";
      }
      return result;
    }

    @Override
    public int compareTo(NameItem o) {
      return project.getPath().compareToIgnoreCase(o.project.getPath());
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
      String path = getPath(mavenProjectToModuleName.get(each), each, dedicatedModuleDir, module);
      mavenProjectToModulePath.put(each, path);
    }
  }

  @NotNull
  public static @NonNls String getPath(@NotNull String moduleName,
                                       @NotNull MavenProject each,
                                       @Nullable String dedicatedModuleDir,
                                       @Nullable Module module) {
    return module != null
           ? module.getModuleFilePath()
           : generateModulePath(each, moduleName, dedicatedModuleDir);
  }

  @NotNull
  public static String generateModulePath(MavenProject project,
                                          String moduleName,
                                          String dedicatedModuleDir) {
    String dir = StringUtil.isEmptyOrSpaces(dedicatedModuleDir)
                 ? project.getDirectory()
                 : dedicatedModuleDir;
    String fileName = moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
    return new File(dir, fileName).getPath();
  }

  @NotNull
  public static String generateModulePath(String directory,
                                          String moduleName,
                                          String dedicatedModuleDir) {
    String dir = StringUtil.isEmptyOrSpaces(dedicatedModuleDir)
                 ? directory
                 : dedicatedModuleDir;
    String fileName = moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
    return new File(dir, fileName).getPath();
  }
}
