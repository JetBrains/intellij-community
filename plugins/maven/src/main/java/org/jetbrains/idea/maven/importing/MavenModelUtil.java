// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.text.VersionComparatorUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.MavenJavaVersionHolder;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.openapi.util.text.StringUtil.compareVersionNumbers;

public final class MavenModelUtil {
  private static final Map<String, LanguageLevel> MAVEN_IDEA_PLUGIN_LEVELS = ImmutableMap.of(
    "JDK_1_3", LanguageLevel.JDK_1_3,
    "JDK_1_4", LanguageLevel.JDK_1_4,
    "JDK_1_5", LanguageLevel.JDK_1_5,
    "JDK_1_6", LanguageLevel.JDK_1_6,
    "JDK_1_7", LanguageLevel.JDK_1_7);

  @NotNull
  public static String getArtifactUrlForClassifierAndExtension(@NotNull MavenArtifact artifact,
                                                               @Nullable String classifier,
                                                               @Nullable String extension) {

    String newPath = artifact.getPathForExtraArtifact(classifier, extension);
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR;
  }

  @NotNull
  public static String getArtifactUrl(@NotNull MavenArtifact artifact,
                                      @NotNull MavenExtraArtifactType artifactType,
                                      @NotNull MavenProject project) {

    Pair<String, String> result = project.getClassifierAndExtension(artifact, artifactType);
    String classifier = result.first;
    String extension = result.second;


    return getArtifactUrlForClassifierAndExtension(artifact, classifier, extension);
  }

  public static @NotNull LanguageLevel getSourceLanguageLevel(@NotNull MavenProject mavenProject) {
    return getLanguageLevel(mavenProject, () -> getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), true, false));
  }

  public static @Nullable LanguageLevel getTargetLanguageLevel(@NotNull MavenProject mavenProject) {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), false, false);
  }

  public static @Nullable LanguageLevel getTargetTestLanguageLevel(@NotNull MavenProject mavenProject) {
    return getMavenLanguageLevel(mavenProject, isReleaseCompilerProp(mavenProject), false, true);
  }

  public static @NotNull LanguageLevel getLanguageLevel(@NotNull MavenProject mavenProject, @NotNull Supplier<LanguageLevel> supplier) {
    LanguageLevel level = null;

    Element cfg = mavenProject.getPluginConfiguration("com.googlecode", "maven-idea-plugin");
    if (cfg != null) {
      level = MAVEN_IDEA_PLUGIN_LEVELS.get(cfg.getChildTextTrim("jdkLevel"));
    }

    if (level == null) {
      level = supplier.get();
    }

    // default source and target settings of maven-compiler-plugin is 1.5 for versions less than 3.8.1 and 1.6 for 3.8.1 and above
    // see details at http://maven.apache.org/plugins/maven-compiler-plugin and https://issues.apache.org/jira/browse/MCOMPILER-335
    if (level == null) {
      level = getDefaultLevel(mavenProject);
    }

    if (level.isAtLeast(LanguageLevel.JDK_11)) {
      level = adjustPreviewLanguageLevel(mavenProject, level);
    }
    return level;
  }

  @NotNull
  public static MavenJavaVersionHolder getMavenJavaVersions(@NotNull MavenProject mavenProject) {
    boolean useReleaseCompilerProp = isReleaseCompilerProp(mavenProject);
    LanguageLevel sourceVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, true, false);
    LanguageLevel sourceTestVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, true, true);
    LanguageLevel targetVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, false, false);
    LanguageLevel targetTestVersion = getMavenLanguageLevel(mavenProject, useReleaseCompilerProp, false, true);
    return new MavenJavaVersionHolder(sourceVersion, targetVersion, sourceTestVersion, targetTestVersion);
  }

    @Nullable
  private static LanguageLevel getMavenLanguageLevel(@NotNull MavenProject mavenProject,
                                                boolean useReleaseCompilerProp,
                                                boolean isSource,
                                                boolean isTest) {
    String mavenProjectReleaseLevel = useReleaseCompilerProp
                                      ? isTest ? mavenProject.getTestReleaseLevel() : mavenProject.getReleaseLevel()
                                      : null;
    LanguageLevel level = LanguageLevel.parse(mavenProjectReleaseLevel);
    if (level == null) {
      String mavenProjectLanguageLevel = getMavenLanguageLevel(mavenProject, isTest, isSource);
      level = LanguageLevel.parse(mavenProjectLanguageLevel);
      if (level == null && (StringUtil.isNotEmpty(mavenProjectLanguageLevel) || StringUtil.isNotEmpty(mavenProjectReleaseLevel))) {
        level = LanguageLevel.HIGHEST;
      }
    }
    return level;
  }

  private static String getMavenLanguageLevel(MavenProject project, boolean test, boolean source) {
    if (test) {
      return source ? project.getTestSourceLevel() : project.getTestTargetLevel();
    }
    else {
      return source ? project.getSourceLevel() : project.getTargetLevel();
    }
  }

  @NotNull
  public static LanguageLevel getDefaultLevel(MavenProject mavenProject) {
    MavenPlugin plugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    if (plugin != null && plugin.getVersion() != null) {
      if (VersionComparatorUtil.compare("3.8.1", plugin.getVersion()) <= 0) {
        return LanguageLevel.JDK_1_6;
      }
      else {
        return LanguageLevel.JDK_1_5;
      }
    }
    return LanguageLevel.JDK_1_5;
  }

  private static LanguageLevel adjustPreviewLanguageLevel(MavenProject mavenProject, LanguageLevel level) {
    Element compilerConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
    if (compilerConfiguration != null) {
      Element compilerArgs = compilerConfiguration.getChild("compilerArgs");
      if (compilerArgs != null) {
        if (isPreviewText(compilerArgs) ||
            compilerArgs.getChildren("arg").stream().anyMatch(MavenModelUtil::isPreviewText) ||
            compilerArgs.getChildren("compilerArg").stream().anyMatch(MavenModelUtil::isPreviewText)) {
          try {
            return LanguageLevel.valueOf(level.name() + "_PREVIEW");
          }
          catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
    return level;
  }

  private static boolean isPreviewText(Element child) {
    return JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY.equals(child.getTextTrim());
  }

  public static boolean isReleaseCompilerProp(@NotNull MavenProject mavenProject) {
    return compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "3.6") >= 0;
  }

  public static boolean isCompilerTestSupport(@NotNull MavenProject mavenProject) {
    return compareVersionNumbers(MavenUtil.getCompilerPluginVersion(mavenProject), "2.1") >= 0;
  }

  public static boolean isMainOrTestSubmodule(@NotNull String moduleName) {
    return moduleName.length() > 5 && moduleName.endsWith(".main") || moduleName.endsWith(".test");
  }
}
