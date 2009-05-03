package org.jetbrains.idea.maven.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenModuleNameMapper {
  public static void map(MavenProjectsTree mavenTree,
                         Map<MavenProject, Module> mavenProjectToModule,
                         Map<MavenProject, String> mavenProjectToModuleName,
                         Map<MavenProject, String> mavenProjectToModulePath,
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
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName) {
    for (MavenProject each : mavenTree.getProjects()) {
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

    for (MavenProject each : mavenTree.getProjects()) {
      if (!each.isValid()) continue;

      String name = mavenProjectToModuleName.get(each);
      if (counts.get(name) > 1) {
        mavenProjectToModuleName.put(each, name + " (" + each.getMavenId().groupId + ")");
      }
    }

    for (MavenProject each : mavenTree.getProjects()) {
      String name = mavenProjectToModuleName.get(each);
      List<MavenProject> withSameName = getProjectsWithName(mavenTree,
                                                                 name,
                                                                 mavenProjectToModuleName);
      if (withSameName.size() > 1) {
        int i = 1;
        for (MavenProject eachWithSameName : withSameName) {
          mavenProjectToModuleName.put(eachWithSameName, name + " (" + i + ")");
          i++;
        }
      }
    }
  }

  private static Map<String, Integer> collectNamesCounts(MavenProjectsTree mavenTree,
                                                         Map<MavenProject, String> mavenProjectToModuleName) {
    Map<String, Integer> result = new THashMap<String, Integer>();

    for (MavenProject each : mavenTree.getProjects()) {
      String name = mavenProjectToModuleName.get(each);
      Integer count = result.get(name);
      if (count == null) count = 0;
      count++;
      result.put(name, count);
    }

    return result;
  }

  private static List<MavenProject> getProjectsWithName(MavenProjectsTree mavenTree,
                                                             String name,
                                                             Map<MavenProject, String> mavenProjectToModuleName) {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (MavenProject each : mavenTree.getProjects()) {
      if (name.equals(mavenProjectToModuleName.get(each))) result.add(each);
    }
    return result;
  }


  private static void resolveModulePaths(MavenProjectsTree mavenTree,
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName,
                                         Map<MavenProject, String> mavenProjectToModulePath,
                                         String dedicatedModuleDir) {
    for (MavenProject each : mavenTree.getProjects()) {
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
