package org.jetbrains.idea.maven.importing;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenId;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenModuleNameMapper {
  public static void map(List<MavenProject> projects,
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

  private static void resolveModuleNames(List<MavenProject> projects,
                                         Map<MavenProject, Module> mavenProjectToModule,
                                         Map<MavenProject, String> mavenProjectToModuleName) {
    for (MavenProject each : projects) {
      String name;
      Module module = mavenProjectToModule.get(each);
      if (module != null) {
        name = module.getName();
      }
      else {
        name = each.getMavenId().getArtifactId();
        if (!isValidName(name)) name = each.getDirectoryFile().getName();
      }
      mavenProjectToModuleName.put(each, name);
    }

    Map<String, Integer> counts = collectNamesCounts(projects,
                                                     mavenProjectToModuleName);

    for (MavenProject each : projects) {
      String name = mavenProjectToModuleName.get(each);
      if (counts.get(name) > 1) {
        String groupId = each.getMavenId().getGroupId();
        if (isValidName(groupId)) {
          mavenProjectToModuleName.put(each, name + " (" + groupId + ")");
        }
      }
    }

    for (MavenProject each : projects) {
      String name = mavenProjectToModuleName.get(each);
      List<MavenProject> withSameName = getProjectsWithName(projects,
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

  private static Map<String, Integer> collectNamesCounts(List<MavenProject> projects,
                                                         Map<MavenProject, String> mavenProjectToModuleName) {
    Map<String, Integer> result = new THashMap<String, Integer>();

    for (MavenProject each : projects) {
      String name = mavenProjectToModuleName.get(each);
      Integer count = result.get(name);
      if (count == null) count = 0;
      count++;
      result.put(name, count);
    }

    return result;
  }

  private static List<MavenProject> getProjectsWithName(List<MavenProject> projects,
                                                        String name,
                                                        Map<MavenProject, String> mavenProjectToModuleName) {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (MavenProject each : projects) {
      if (name.equals(mavenProjectToModuleName.get(each))) result.add(each);
    }
    return result;
  }

  private static void resolveModulePaths(List<MavenProject> projects,
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
