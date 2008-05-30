package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenModuleNameMapper {
  public static void map(MavenProjectModel mavenProjectModel, String moduleDir) {
    resolveModuleNames(mavenProjectModel);
    resolveModulePaths(mavenProjectModel, moduleDir);
  }

  private static void resolveModuleNames(final MavenProjectModel model) {
    for (MavenProjectModel.Node each : model.getProjects()) {
      String name;
      if (each.getModule() != null) {
        name = each.getModule().getName();
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

    for (MavenProjectModel.Node each : model.getProjects()) {
      if (!each.isValid()) continue;

      String name = each.getModuleName();
      if (counts.get(name) > 1) {
        each.setModuleName(name + " (" + each.getMavenId().groupId + ")");
      }
    }

    for (MavenProjectModel.Node each : model.getProjects()) {
      List<MavenProjectModel.Node> withSameName = getProjectsWithName(model, each.getModuleName());
      if (withSameName.size() > 1) {
        int i = 1;
        for (MavenProjectModel.Node eachWithSameName : withSameName) {
          String name = eachWithSameName.getModuleName();
          eachWithSameName.setModuleName(name + " (" + i + ")");
          i++;
        }
      }
    }
  }

  private static Map<String, Integer> collectNamesCounts(MavenProjectModel model) {
    Map<String, Integer> result = new HashMap<String, Integer>();

    for (MavenProjectModel.Node each : model.getProjects()) {
      String name = each.getModuleName();
      Integer count = result.get(name);
      if (count == null) count = 0;
      count++;
      result.put(name, count);
    }

    return result;
  }

  private static List<MavenProjectModel.Node> getProjectsWithName(MavenProjectModel model, String name) {
    List<MavenProjectModel.Node> result = new ArrayList<MavenProjectModel.Node>();
    for (MavenProjectModel.Node each : model.getProjects()) {
      if (each.getModuleName().equals(name)) result.add(each);
    }
    return result;
  }


  private static void resolveModulePaths(final MavenProjectModel mavenProjectModel, final String dedicatedModuleDir) {
    mavenProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(final MavenProjectModel.Node node) {
        final Module module = node.getModule();
        node.setModuleFilePath(module != null ? module.getModuleFilePath() : generateModulePath(node, dedicatedModuleDir));
      }
    });
  }

  private static String generateModulePath(MavenProjectModel.Node node, String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    node.getModuleName() + Constants.IML_EXT).getPath();
  }
}