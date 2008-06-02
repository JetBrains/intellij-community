package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenModuleNameMapper {
  public static void map(MavenProjectModelManager mavenProjectModel, String moduleDir) {
    resolveModuleNames(mavenProjectModel);
    resolveModulePaths(mavenProjectModel, moduleDir);
  }

  private static void resolveModuleNames(final MavenProjectModelManager model) {
    for (MavenProjectModel each : model.getProjects()) {
      String name;
      if (each.getIdeaModule() != null) {
        name = each.getIdeaModule().getName();
      }
      else if (each.isValid()) {
        name = each.getMavenId().artifactId;
      }
      else {
        name = each.getDirectoryFile().getName();
      }
      each.setModuleName(name);
    }

    Map<String, Integer> counts = collectNamesCounts(model);

    for (MavenProjectModel each : model.getProjects()) {
      if (!each.isValid()) continue;

      String name = each.getModuleName();
      if (counts.get(name) > 1) {
        each.setModuleName(name + " (" + each.getMavenId().groupId + ")");
      }
    }

    for (MavenProjectModel each : model.getProjects()) {
      List<MavenProjectModel> withSameName = getProjectsWithName(model, each.getModuleName());
      if (withSameName.size() > 1) {
        int i = 1;
        for (MavenProjectModel eachWithSameName : withSameName) {
          String name = eachWithSameName.getModuleName();
          eachWithSameName.setModuleName(name + " (" + i + ")");
          i++;
        }
      }
    }
  }

  private static Map<String, Integer> collectNamesCounts(MavenProjectModelManager model) {
    Map<String, Integer> result = new HashMap<String, Integer>();

    for (MavenProjectModel each : model.getProjects()) {
      String name = each.getModuleName();
      Integer count = result.get(name);
      if (count == null) count = 0;
      count++;
      result.put(name, count);
    }

    return result;
  }

  private static List<MavenProjectModel> getProjectsWithName(MavenProjectModelManager model, String name) {
    List<MavenProjectModel> result = new ArrayList<MavenProjectModel>();
    for (MavenProjectModel each : model.getProjects()) {
      if (each.getModuleName().equals(name)) result.add(each);
    }
    return result;
  }


  private static void resolveModulePaths(final MavenProjectModelManager mavenProjectModel, final String dedicatedModuleDir) {
    mavenProjectModel.visit(new MavenProjectModelManager.SimpleVisitor() {
      public void visit(final MavenProjectModel node) {
        final Module module = node.getIdeaModule();
        node.setModuleFilePath(module != null ? module.getModuleFilePath() : generateModulePath(node, dedicatedModuleDir));
      }
    });
  }

  private static String generateModulePath(MavenProjectModel node, String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    node.getModuleName() + Constants.IML_EXT).getPath();
  }
}