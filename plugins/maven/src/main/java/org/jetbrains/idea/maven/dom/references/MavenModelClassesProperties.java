// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MavenModelClassesProperties {

  private static final Map<String, Map<String, String>> PROPERTIES_MAP;

  public static final String MAVEN_PROJECT_CLASS = "org.apache.maven.project.MavenProject";
  public static final String MAVEN_MODEL_CLASS = "org.apache.maven.model.Model";

  static {
    Map<String, Map<String, String>> res = new HashMap<>();

    res.put(MAVEN_PROJECT_CLASS, Map.<String, String>ofEntries(
      Map.entry("parentFile", "java.io.File"),
      Map.entry("artifact", "org.apache.maven.artifact.Artifact"),
      Map.entry("model", MAVEN_MODEL_CLASS),
      Map.entry("parent", MAVEN_PROJECT_CLASS),
      Map.entry("file", "java.io.File"),
      Map.entry("dependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("compileSourceRoots", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("scriptSourceRoots", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("testCompileSourceRoots", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("compileClasspathElements", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("compileArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("compileDependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("testClasspathElements", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("testArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("testDependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("runtimeClasspathElements", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("runtimeArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("runtimeDependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("systemClasspathElements", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("systemArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("systemDependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("modelVersion", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("id", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("groupId", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("artifactId", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("version", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("packaging", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("name", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("inceptionYear", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("url", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("prerequisites", "org.apache.maven.model.Prerequisites"),
      Map.entry("issueManagement", "org.apache.maven.model.IssueManagement"),
      Map.entry("ciManagement", "org.apache.maven.model.CiManagement"),
      Map.entry("description", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("organization", "org.apache.maven.model.Organization"),
      Map.entry("scm", "org.apache.maven.model.Scm"),
      Map.entry("mailingLists", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("developers", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("contributors", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("build", "org.apache.maven.model.Build"),
      Map.entry("resources", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("testResources", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("reporting", "org.apache.maven.model.Reporting"),
      Map.entry("licenses", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("artifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("artifactMap", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("pluginArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("pluginArtifactMap", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("reportArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("reportArtifactMap", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("extensionArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("extensionArtifactMap", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("parentArtifact", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("repositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("reportPlugins", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("buildPlugins", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("modules", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("modelBuild", "org.apache.maven.model.Build"),
      Map.entry("remoteArtifactRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("pluginArtifactRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("distributionManagementArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository"),
      Map.entry("pluginRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("remoteProjectRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("remotePluginRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("activeProfiles", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("injectedProfileIds", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("attachedArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("executionProject", MAVEN_PROJECT_CLASS),
      Map.entry("collectedProjects", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("dependencyArtifacts", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("managedVersionMap", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("buildExtensions", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("properties", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("filters", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("projectReferences", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("executionRoot", "boolean"),
      Map.entry("defaultGoal", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("releaseArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository"),
      Map.entry("snapshotArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository"),
      Map.entry("classRealm", "org.codehaus.plexus.classworlds.realm.ClassRealm"),
      Map.entry("extensionDependencyFilter", "org.sonatype.aether.graph.DependencyFilter"),
      Map.entry("projectBuildingRequest", "org.apache.maven.project.ProjectBuildingRequest"))
    );

    res.put(MAVEN_MODEL_CLASS, Map.ofEntries(
      Map.entry("modelVersion", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("parent", MAVEN_PROJECT_CLASS),
      Map.entry("groupId", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("artifactId", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("version", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("packaging", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("name", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("description", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("url", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("inceptionYear", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("organization", "org.apache.maven.model.Organization"),
      Map.entry("licenses", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("developers", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("contributors", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("mailingLists", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("prerequisites", "org.apache.maven.model.Prerequisites"),
      Map.entry("scm", "org.apache.maven.model.Scm"),
      Map.entry("issueManagement", "org.apache.maven.model.IssueManagement"),
      Map.entry("ciManagement", "org.apache.maven.model.CiManagement"),
      Map.entry("build", "org.apache.maven.model.Build"),
      Map.entry("profiles", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("modelEncoding", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("pomFile", "java.io.File"),
      Map.entry("projectDirectory", "java.io.File"),
      Map.entry("id", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("repositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("dependencies", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("modules", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("pluginRepositories", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("properties", CommonClassNames.JAVA_UTIL_LIST),
      Map.entry("reports", CommonClassNames.JAVA_LANG_OBJECT),
      Map.entry("reporting", "org.apache.maven.model.Reporting"))
    );

    res.put(CommonClassNames.JAVA_UTIL_LIST, Map.of("empty", "boolean"));

    res.put("org.apache.maven.model.Build", Map.of(
      "extensions", CommonClassNames.JAVA_UTIL_LIST,
      "filters", CommonClassNames.JAVA_UTIL_LIST,
      "resources", CommonClassNames.JAVA_UTIL_LIST,
      "testResources", CommonClassNames.JAVA_UTIL_LIST)
      );

    res.put("java.io.File", Map.ofEntries(
      Map.entry("prefixLength", "long"),
      Map.entry("name", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("parent", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("parentFile", "java.io.File"),
      Map.entry("path", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("absolute", "boolean"),
      Map.entry("absolutePath", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("absoluteFile", "java.io.File"),
      Map.entry("canonicalPath", CommonClassNames.JAVA_LANG_STRING),
      Map.entry("canonicalFile", "java.io.File"),
      Map.entry("directory", "boolean"),
      Map.entry("file", "boolean"),
      Map.entry("hidden", "boolean"),
      Map.entry("totalSpace", "long"),
      Map.entry("freeSpace", "long"),
      Map.entry("usableSpace", "long"))
      );

    PROPERTIES_MAP = res;
  }

  public static boolean isPathValid(@NotNull String className, @NotNull String path) {
    Map<String,String> cMap = PROPERTIES_MAP.get(className);
    if (cMap == null) return false;

    int idx = 0;

    do {
      int i = path.indexOf('.', idx);
      if (i == -1) {
        return cMap.containsKey(path.substring(idx));
      }

      cMap = PROPERTIES_MAP.get(cMap.get(path.substring(idx, i)));
      if (cMap == null) return false;

      idx = i + 1;
    } while (true);
  }

  public static Map<String, String> getCompletionVariants(@NotNull String className, @NotNull String path) {
    Map<String,String> cMap = PROPERTIES_MAP.get(className);
    if (cMap == null) return Collections.emptyMap();

    int idx = 0;

    do {
      int i = path.indexOf('.', idx);
      if (i == -1) {
        return cMap;
      }

      cMap = PROPERTIES_MAP.get(cMap.get(path.substring(idx, i)));
      if (cMap == null) return Collections.emptyMap();

      idx = i + 1;
    } while (true);
  }
}
