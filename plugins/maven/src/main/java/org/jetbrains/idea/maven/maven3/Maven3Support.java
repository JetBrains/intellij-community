// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven3;

import com.intellij.maven.server.telemetry.MavenServerTelemetryClasspathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.BundledMaven3;
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenServer;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.impl.BundledMavenDownloader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Maven3Support implements MavenVersionAwareSupportExtension {
  private static final @NonNls String MAIN_CLASS36 = "org.jetbrains.idea.maven.server.RemoteMavenServer36";

  @Override
  public boolean isSupportedByExtension(@Nullable File mavenHome) {
    String version = MavenUtil.getMavenVersion(mavenHome.toPath());
    return StringUtil.compareVersionNumbers(version, "3.1") >= 0 && StringUtil.compareVersionNumbers(version, "4") < 0;
  }

  @Override
  public @Nullable Path getMavenHomeFile(@Nullable StaticResolvedMavenHomeType mavenHomeType) {
    if (mavenHomeType == null) return null;
    if (mavenHomeType == BundledMaven3.INSTANCE) {
      return MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome();
    }
    return null;
  }

  @Override
  public @NotNull List<Path> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution) {
    final Path pluginFileOrDir = Path.of(PathUtil.getJarPathForClass(MavenServerManager.class));

    final List<Path> classpath = new ArrayList<>();
    String relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation();

    if (Files.isDirectory(pluginFileOrDir)) {
      MavenLog.LOG.debug("collecting classpath for local run");
      prepareClassPathForLocalRunAndUnitTests(distribution.getVersion(), classpath, pluginFileOrDir.toFile().getParent());
    }
    else if (relevantJarsRoot != null && pluginFileOrDir.startsWith(relevantJarsRoot)) {
      MavenLog.LOG.debug("collecting classpath for local run on archived outputs");
      prepareClassPathForLocalRunAndUnitTestsArchived(distribution.getVersion(), classpath);
    }
    else {
      MavenLog.LOG.debug("collecting classpath for production");
      prepareClassPathForProduction(distribution.getVersion(), classpath, pluginFileOrDir.toFile().getParent());
    }

    addMavenLibs(classpath, distribution.getMavenHome());
    MavenLog.LOG.debug("Collected classpath = ", classpath);
    return classpath;
  }

  private static void prepareClassPathForProduction(@NotNull String mavenVersion,
                                                    List<Path> classpath,
                                                    String root) {
    Path rootPath = Path.of(root);

    classpath.add(Path.of(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(rootPath.resolve("maven-server.jar"));

    classpath.add(rootPath.resolve("maven-server-telemetry.jar"));
    try {
      classpath.add(Path.of(PathUtil.getJarPathForClass(Class.forName("io.opentelemetry.sdk.trace.export.SpanExporter"))));
    }
    catch (ClassNotFoundException e) {
      MavenLog.LOG.error(e);
    }
    addDir(classpath, rootPath.resolve("maven-telemetry-lib"), f -> true);

    classpath.add(Path.of(root, "maven3-server-common.jar"));
    addDir(classpath, Path.of(root, "maven3-server-lib"), f -> true);

    classpath.add(Path.of(root, "maven3-server.jar"));
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
      classpath.add(Path.of(root, "maven36-server.jar"));
    }
  }

  private static void prepareClassPathForLocalRunAndUnitTests(@NotNull String mavenVersion, List<Path> classpath, String root) {
    BuildDependenciesCommunityRoot communityRoot = new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()));
    BundledMavenDownloader.INSTANCE.downloadMaven3LibsSync(communityRoot);
    Path rootPath = Path.of(root);

    classpath.add(Path.of(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(rootPath.resolve("intellij.maven.server"));

    classpath.add(rootPath.resolve("intellij.maven.server.telemetry"));
    classpath.addAll(MavenUtil.collectClasspath(MavenServerTelemetryClasspathUtil.TELEMETRY_CLASSES));

    Path parentFile = MavenUtil.getMavenPluginParentFile();
    classpath.add(rootPath.resolve("intellij.maven.server.m3.common"));
    addDir(classpath, parentFile.resolve("maven3-server-common/lib"), f -> true);

    classpath.add(rootPath.resolve("intellij.maven.server.m3.impl"));
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
      classpath.add(rootPath.resolve("intellij.maven.server.m36.impl"));
    }
  }

  private static void prepareClassPathForLocalRunAndUnitTestsArchived(@NotNull String mavenVersion, List<Path> classpath) {
    BuildDependenciesCommunityRoot communityRoot = new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()));
    BundledMavenDownloader.INSTANCE.downloadMaven3LibsSync(communityRoot);

    final Map<String, String> mapping = PathManager.getArchivedCompiledClassesMapping();
    if (mapping == null) throw new IllegalStateException("Mapping cannot be null at this point");

    classpath.add(Path.of(PathUtil.getJarPathForClass(MavenId.class)));
    classpath.add(Path.of(mapping.get("production/intellij.maven.server")));

    classpath.add(Path.of(mapping.get("production/intellij.maven.server.telemetry")));
    classpath.addAll(MavenUtil.collectClasspath(MavenServerTelemetryClasspathUtil.TELEMETRY_CLASSES));

    Path parentPath = MavenUtil.getMavenPluginParentFile();
    classpath.add(Path.of(mapping.get("production/intellij.maven.server.m3.common")));
    addDir(classpath, parentPath.resolve("maven3-server-common/lib"), f -> true);

    classpath.add(Path.of(mapping.get("production/intellij.maven.server.m3.impl")));
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.6") >= 0) {
      classpath.add(Path.of(mapping.get("production/intellij.maven.server.m36.impl")));
    }
  }

  private static void addMavenLibs(List<Path> classpath, Path mavenHome) {
    addDir(classpath, mavenHome.resolve("lib"), f -> !f.getFileName().toString().contains("maven-slf4j-provider"));
    Path bootFolder = mavenHome.resolve("boot");
    List<Path> classworldsJars =
      NioFiles.list(bootFolder).stream().filter((f) -> StringUtil.contains(f.getFileName().toString(), "classworlds")).toList();
    classpath.addAll(classworldsJars);
  }

  private static void addDir(List<Path> classpath, Path dir, Predicate<Path> filter) {
    List<Path> files = NioFiles.list(dir);

    for (Path jar : files) {
      if (Files.isRegularFile(jar) && jar.getFileName().toString().endsWith(".jar") && filter.test(jar)) {
        classpath.add(jar);
      }
    }
  }

  @Override
  public String getMainClass(MavenDistribution distribution) {
    if (StringUtil.compareVersionNumbers(distribution.getVersion(), "3.6") >= 0) {
      return MAIN_CLASS36;
    } else {
      return DEFAULT_MAIN_CLASS;
    }

  }
}
