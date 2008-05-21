package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModuleNameMapper {
  public static void map(MavenProjectModel mavenProjectModel, String moduleDir) {
    resolveModuleNames(mavenProjectModel);
    resolveModulePaths(mavenProjectModel, moduleDir);
  }

  private static void resolveModuleNames(final MavenProjectModel model) {
    final List<String> duplicateNames = collectDuplicateModuleNames(model);

    model.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        MavenId id = node.getMavenId();
        String name = node.getModule() != null
                      ? node.getModule().getName()
                      : generateModuleName(id, duplicateNames);

        node.setModuleName(name);
      }
    });
  }

  private static List<String> collectDuplicateModuleNames(MavenProjectModel m) {
    final List<String> allNames = new ArrayList<String>();
    final List<String> result = new ArrayList<String>();

    m.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        String name = node.getMavenId().artifactId;
        if (allNames.contains(name)) result.add(name);
        allNames.add(name);
      }
    });

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

  private static String generateModuleName(MavenId id, List<String> duplicateNames) {
    String name = id.artifactId;
    if (duplicateNames.contains(name)) name = name + " (" + id.groupId + ")";
    return name;
  }

  private static String generateModulePath(MavenProjectModel.Node node, String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    node.getModuleName() + Constants.IML_EXT).getPath();
  }
}