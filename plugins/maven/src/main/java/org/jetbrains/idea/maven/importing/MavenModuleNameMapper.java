// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.*;

import static java.util.Locale.ROOT;

public final class MavenModuleNameMapper {
  public static Map<MavenProject, String> mapModuleNames(MavenProjectsTree tree,
                                                         Collection<MavenProject> projects,
                                                         Map<VirtualFile, String> existingPomModuleName) {
    var mavenProjectToModuleName = new HashMap<MavenProject, String>();
    NameItem[] names = new NameItem[projects.size()];

    int i = 0;
    for (MavenProject each : projects) {
      names[i++] = new NameItem(tree, each, existingPomModuleName.get(each.getFile()));
    }

    Arrays.sort(names);

    Map<String, Integer> nameCountersLowerCase = new HashMap<>();

    for (i = 0; i < names.length; i++) {
      if (names[i].hasDuplicatedGroup) continue;

      for (int k = i + 1; k < names.length; k++) {
        // IDEA-320329 check should be non-case-sensitive
        if (names[i].originalName.equalsIgnoreCase(names[k].originalName)) {
          nameCountersLowerCase.put(names[i].originalName.toLowerCase(ROOT), 0);

          if (names[i].groupId.equals(names[k].groupId)) {
            names[i].hasDuplicatedGroup = true;
            names[k].hasDuplicatedGroup = true;
          }
        }
      }
    }

    Set<String> existingNames = new HashSet<>();

    for (NameItem name : names) {
      if (name.existingName != null) {
        existingNames.add(name.getResultName());
      }
    }

    for (NameItem nameItem : names) {
      if (nameItem.existingName == null) {

        Integer c = nameCountersLowerCase.get(nameItem.originalName.toLowerCase(ROOT));

        if (c != null) {
          nameItem.number = c;
          nameCountersLowerCase.put(nameItem.originalName.toLowerCase(ROOT), c + 1);
        }

        do {
          String name = nameItem.getResultName();
          if (existingNames.add(name)) break;

          nameItem.number++;
          nameCountersLowerCase.put(nameItem.originalName.toLowerCase(ROOT), nameItem.number + 1);
        }
        while (true);
      }
    }

    for (NameItem each : names) {
      mavenProjectToModuleName.put(each.project, each.getResultName());
    }

    return mavenProjectToModuleName;
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

  private static class NameItem implements Comparable<NameItem> {
    private final MavenProjectsTree tree;
    public final MavenProject project;
    public final String existingName;

    public final String originalName;
    public final String groupId;

    public int number = -1; // has no duplicates
    public boolean hasDuplicatedGroup;

    private NameItem(MavenProjectsTree tree, MavenProject project, @Nullable String existingName) {
      this.tree = tree;
      this.project = project;
      this.existingName = existingName;
      originalName = calcOriginalName();

      String group = project.getMavenId().getGroupId();
      groupId = isValidName(group) ? group : "";
    }

    private String calcOriginalName() {
      if (existingName != null) return existingName;

      return getDefaultModuleName();
    }

    private String getDefaultModuleName() {
      var nameTemplate = Registry.stringValue("maven.import.module.name.template");
      var folderName = project.getDirectoryFile().getName();
      var mavenId = project.getMavenId();
      var nameCandidate = switch (nameTemplate) {
        case "folderName" -> folderName;
        case "groupId.artifactId" -> mavenId.getGroupId() + "." + mavenId.getArtifactId();
        case "aggregatorArtifactId.artifactId" -> aggregatorArtifactIdPrefix() + mavenId.getArtifactId();
        default -> mavenId.getArtifactId();
      };
      return isValidName(nameCandidate) ? nameCandidate : folderName;
    }

    private String aggregatorArtifactIdPrefix() {
      var aggregator = tree.findAggregator(project);
      return null == aggregator ? "" : aggregator.getMavenId().getArtifactId() + ".";
    }

    public String getResultName() {
      if (existingName != null) return existingName;

      if (number == -1) return originalName;
      String result = originalName + " (" + (number + 1) + ")";
      if (!hasDuplicatedGroup && !groupId.isEmpty()) {
        result += " (" + groupId + ")";
      }
      return result;
    }

    @Override
    public int compareTo(NameItem o) {
      return project.getPath().compareToIgnoreCase(o.project.getPath());
    }
  }
}
