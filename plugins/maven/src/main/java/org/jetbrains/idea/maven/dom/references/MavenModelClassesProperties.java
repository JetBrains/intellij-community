/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.references;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenModelClassesProperties {

  private static final Map<String, Map<String, String>> PROPERTIES_MAP;

  public static final String MAVEN_PROJECT_CLASS = "org.apache.maven.project.MavenProject";
  public static final String MAVEN_MODEL_CLASS = "org.apache.maven.model.Model";

  static {
    Map<String, Map<String, String>> res = new HashMap<>();

    res.put(MAVEN_PROJECT_CLASS, ContainerUtil.<String, String>immutableMapBuilder()
      .put("parentFile", "java.io.File")
      .put("artifact", "org.apache.maven.artifact.Artifact")
      .put("model", MAVEN_MODEL_CLASS)
      .put("parent", MAVEN_PROJECT_CLASS)
      .put("file", "java.io.File")
      .put("dependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("compileSourceRoots", CommonClassNames.JAVA_UTIL_LIST)
      .put("scriptSourceRoots", CommonClassNames.JAVA_UTIL_LIST)
      .put("testCompileSourceRoots", CommonClassNames.JAVA_UTIL_LIST)
      .put("compileClasspathElements", CommonClassNames.JAVA_UTIL_LIST)
      .put("compileArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("compileDependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("testClasspathElements", CommonClassNames.JAVA_UTIL_LIST)
      .put("testArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("testDependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("runtimeClasspathElements", CommonClassNames.JAVA_UTIL_LIST)
      .put("runtimeArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("runtimeDependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("systemClasspathElements", CommonClassNames.JAVA_UTIL_LIST)
      .put("systemArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("systemDependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("modelVersion", CommonClassNames.JAVA_LANG_STRING)
      .put("id", CommonClassNames.JAVA_LANG_STRING)
      .put("groupId", CommonClassNames.JAVA_LANG_STRING)
      .put("artifactId", CommonClassNames.JAVA_LANG_STRING)
      .put("version", CommonClassNames.JAVA_LANG_STRING)
      .put("packaging", CommonClassNames.JAVA_LANG_STRING)
      .put("name", CommonClassNames.JAVA_LANG_STRING)
      .put("inceptionYear", CommonClassNames.JAVA_LANG_STRING)
      .put("url", CommonClassNames.JAVA_LANG_STRING)
      .put("prerequisites", "org.apache.maven.model.Prerequisites")
      .put("issueManagement", "org.apache.maven.model.IssueManagement")
      .put("ciManagement", "org.apache.maven.model.CiManagement")
      .put("description", CommonClassNames.JAVA_LANG_STRING)
      .put("organization", "org.apache.maven.model.Organization")
      .put("scm", "org.apache.maven.model.Scm")
      .put("mailingLists", CommonClassNames.JAVA_UTIL_LIST)
      .put("developers", CommonClassNames.JAVA_UTIL_LIST)
      .put("contributors", CommonClassNames.JAVA_UTIL_LIST)
      .put("build", "org.apache.maven.model.Build")

      .put("resources", CommonClassNames.JAVA_UTIL_LIST)
      .put("testResources", CommonClassNames.JAVA_UTIL_LIST)
      .put("reporting", "org.apache.maven.model.Reporting")
      .put("licenses", CommonClassNames.JAVA_UTIL_LIST)
      .put("artifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("artifactMap", CommonClassNames.JAVA_UTIL_LIST)
      .put("pluginArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("pluginArtifactMap", CommonClassNames.JAVA_UTIL_LIST)
      .put("reportArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("reportArtifactMap", CommonClassNames.JAVA_UTIL_LIST)
      .put("extensionArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("extensionArtifactMap", CommonClassNames.JAVA_UTIL_LIST)
      .put("parentArtifact", CommonClassNames.JAVA_UTIL_LIST)
      .put("repositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("reportPlugins", CommonClassNames.JAVA_UTIL_LIST)
      .put("buildPlugins", CommonClassNames.JAVA_UTIL_LIST)
      .put("modules", CommonClassNames.JAVA_UTIL_LIST)
      .put("modelBuild", "org.apache.maven.model.Build")
      .put("remoteArtifactRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("pluginArtifactRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("distributionManagementArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
      .put("pluginRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("remoteProjectRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("remotePluginRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("activeProfiles", CommonClassNames.JAVA_UTIL_LIST)
      .put("injectedProfileIds", CommonClassNames.JAVA_UTIL_LIST)
      .put("attachedArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("executionProject", MAVEN_PROJECT_CLASS)
      .put("collectedProjects", CommonClassNames.JAVA_UTIL_LIST)
      .put("dependencyArtifacts", CommonClassNames.JAVA_UTIL_LIST)
      .put("managedVersionMap", CommonClassNames.JAVA_UTIL_LIST)
      .put("buildExtensions", CommonClassNames.JAVA_UTIL_LIST)
      .put("properties", CommonClassNames.JAVA_UTIL_LIST)
      .put("filters", CommonClassNames.JAVA_UTIL_LIST)
      .put("projectReferences", CommonClassNames.JAVA_UTIL_LIST)
      .put("executionRoot", "boolean")
      .put("defaultGoal", CommonClassNames.JAVA_UTIL_LIST)
      .put("releaseArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
      .put("snapshotArtifactRepository", "org.apache.maven.artifact.repository.ArtifactRepository")
      .put("classRealm", "org.codehaus.plexus.classworlds.realm.ClassRealm")
      .put("extensionDependencyFilter", "org.sonatype.aether.graph.DependencyFilter")
      .put("projectBuildingRequest", "org.apache.maven.project.ProjectBuildingRequest")

      .build()
    );

    res.put(MAVEN_MODEL_CLASS, ImmutableMap.<String, String>builder()
      .put("modelVersion", CommonClassNames.JAVA_LANG_STRING)
      .put("parent", MAVEN_PROJECT_CLASS)
      .put("groupId", CommonClassNames.JAVA_LANG_STRING)
      .put("artifactId", CommonClassNames.JAVA_LANG_STRING)
      .put("version", CommonClassNames.JAVA_LANG_STRING)
      .put("packaging", CommonClassNames.JAVA_LANG_STRING)
      .put("name", CommonClassNames.JAVA_LANG_STRING)
      .put("description", CommonClassNames.JAVA_LANG_STRING)
      .put("url", CommonClassNames.JAVA_LANG_STRING)
      .put("inceptionYear", CommonClassNames.JAVA_LANG_STRING)
      .put("organization", "org.apache.maven.model.Organization")
      .put("licenses", CommonClassNames.JAVA_UTIL_LIST)
      .put("developers", CommonClassNames.JAVA_UTIL_LIST)
      .put("contributors", CommonClassNames.JAVA_UTIL_LIST)
      .put("mailingLists", CommonClassNames.JAVA_UTIL_LIST)
      .put("prerequisites", "org.apache.maven.model.Prerequisites")
      .put("scm", "org.apache.maven.model.Scm")
      .put("issueManagement", "org.apache.maven.model.IssueManagement")
      .put("ciManagement", "org.apache.maven.model.CiManagement")
      .put("build", "org.apache.maven.model.Build")
      .put("profiles", CommonClassNames.JAVA_UTIL_LIST)
      .put("modelEncoding", CommonClassNames.JAVA_LANG_STRING)
      .put("pomFile", "java.io.File")
      .put("projectDirectory", "java.io.File")
      .put("id", CommonClassNames.JAVA_LANG_STRING)

      .put("repositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("dependencies", CommonClassNames.JAVA_UTIL_LIST)
      .put("modules", CommonClassNames.JAVA_UTIL_LIST)
      .put("pluginRepositories", CommonClassNames.JAVA_UTIL_LIST)
      .put("properties", CommonClassNames.JAVA_UTIL_LIST)
      .put("reports", CommonClassNames.JAVA_LANG_OBJECT)
      .put("reporting", "org.apache.maven.model.Reporting")
      .build()
    );

    res.put(CommonClassNames.JAVA_UTIL_LIST, ImmutableMap.<String, String>of("empty", "boolean"));

    res.put("org.apache.maven.model.Build", ImmutableMap.<String, String>builder()
      .put("extensions", CommonClassNames.JAVA_UTIL_LIST)
      .put("filters", CommonClassNames.JAVA_UTIL_LIST)
      .put("resources", CommonClassNames.JAVA_UTIL_LIST)
      .put("testResources", CommonClassNames.JAVA_UTIL_LIST)
      .build());

    res.put("java.io.File", ImmutableMap.<String, String>builder()
      .put("prefixLength", "long")
      .put("name", CommonClassNames.JAVA_LANG_STRING)
      .put("parent", CommonClassNames.JAVA_LANG_STRING)
      .put("parentFile", "java.io.File")
      .put("path", CommonClassNames.JAVA_LANG_STRING)
      .put("absolute", "boolean")
      .put("absolutePath", CommonClassNames.JAVA_LANG_STRING)
      .put("absoluteFile", "java.io.File")
      .put("canonicalPath", CommonClassNames.JAVA_LANG_STRING)
      .put("canonicalFile", "java.io.File")
      .put("directory", "boolean")
      .put("file", "boolean")
      .put("hidden", "boolean")
      .put("totalSpace", "long")
      .put("freeSpace", "long")
      .put("usableSpace", "long")
      .build());

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
