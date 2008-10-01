package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.idea.maven.utils.MavenConstants;

public class MavenModuleNameMapper {
  public static void map(MavenProjectsTree mavenTree,
                         Map<MavenProjectModel, Module> mavenProjectToModule,
                         Map<MavenProjectModel, String> mavenProjectToModuleName,
                         Map<MavenProjectModel, String> mavenProjectToModulePath,
                         String dedicatedModuleDir) {
    resolveModuleNames(mavenTree,
                       mavenProjectToModule,
                       mavenProjectToModuleName);
    resolveModulePaths(mavenTree,
                       mavenProjectToModule,
                       mavenProjectToModuleName,
                       mavenProjectToModulePath,
                       dedicatedModuleDir);
  }

  private static void resolveModuleNames(MavenProjectsTree mavenTree,
                                         Map<MavenProjectModel, Module> mavenProjectToModule,
                                         Map<MavenProjectModel, String> mavenProjectToModuleName) {
    for (MavenProjectModel each : mavenTree.getProjects()) {
      String name;
      Module module = mavenProjectToModule.get(each);
      if (module != null) {
        name = module.getName();
      }
      else if (each.isValid()) {
        name = each.getMavenId().artifactId;
      }
      else {
        name = each.getDirectoryFile().getName();
      }
      mavenProjectToModuleName.put(each, name);
    }

    Map<String, Integer> counts = collectNamesCounts(mavenTree,
                                                     mavenProjectToModuleName);

    for (MavenProjectModel each : mavenTree.getProjects()) {
      if (!each.isValid()) continue;

      String name = mavenProjectToModuleName.get(each);
      if (counts.get(name) > 1) {
        mavenProjectToModuleName.put(each, name + " (" + each.getMavenId().groupId + ")");
      }
    }

    for (MavenProjectModel each : mavenTree.getProjects()) {
      String name = mavenProjectToModuleName.get(each);
      List<MavenProjectModel> withSameName = getProjectsWithName(mavenTree,
                                                                 name,
                                                                 mavenProjectToModuleName);
      if (withSameName.size() > 1) {
        int i = 1;
        for (MavenProjectModel eachWithSameName : withSameName) {
          mavenProjectToModuleName.put(eachWithSameName, name + " (" + i + ")");
          i++;
        }
      }
    }
  }

  private static Map<String, Integer> collectNamesCounts(MavenProjectsTree mavenTree,
                                                         Map<MavenProjectModel, String> mavenProjectToModuleName) {
    Map<String, Integer> result = new HashMap<String, Integer>();

    for (MavenProjectModel each : mavenTree.getProjects()) {
      String name = mavenProjectToModuleName.get(each);
      Integer count = result.get(name);
      if (count == null) count = 0;
      count++;
      result.put(name, count);
    }

    return result;
  }

  private static List<MavenProjectModel> getProjectsWithName(MavenProjectsTree mavenTree,
                                                             String name,
                                                             Map<MavenProjectModel, String> mavenProjectToModuleName) {
    List<MavenProjectModel> result = new ArrayList<MavenProjectModel>();
    for (MavenProjectModel each : mavenTree.getProjects()) {
      if (name.equals(mavenProjectToModuleName.get(each))) result.add(each);
    }
    return result;
  }


  private static void resolveModulePaths(MavenProjectsTree mavenTree,
                                         Map<MavenProjectModel, Module> mavenProjectToModule,
                                         Map<MavenProjectModel, String> mavenProjectToModuleName,
                                         Map<MavenProjectModel, String> mavenProjectToModulePath,
                                         String dedicatedModuleDir) {
    for (MavenProjectModel each : mavenTree.getProjects()) {
      Module module = mavenProjectToModule.get(each);
      String path = module != null
                    ? module.getModuleFilePath()
                    : generateModulePath(each,
                                         mavenProjectToModuleName,
                                         dedicatedModuleDir);
      mavenProjectToModulePath.put(each, path);
    }
  }

  private static String generateModulePath(MavenProjectModel project,
                                           Map<MavenProjectModel, String> mavenProjectToModuleName,
                                           String dedicatedModuleDir) {
    String dir = StringUtil.isEmptyOrSpaces(dedicatedModuleDir)
                 ? project.getDirectory()
                 : dedicatedModuleDir;
    String fileName = mavenProjectToModuleName.get(project) + MavenConstants.IML_EXT;
    return new File(dir, fileName).getPath();
  }
}