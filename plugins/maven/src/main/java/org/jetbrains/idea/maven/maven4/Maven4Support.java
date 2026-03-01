// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven4;

import com.intellij.maven.server.telemetry.MavenServerTelemetryClasspathUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.BundledMaven4;
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.impl.BundledMavenDownloader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.maven.MavenClasspathBuilder.addDir;
import static org.jetbrains.idea.maven.MavenClasspathBuilder.addMavenLibs;
import static org.jetbrains.idea.maven.MavenClasspathBuilder.addMavenServerLibraries;
import static org.jetbrains.idea.maven.utils.MavenUtil.locateModuleOutput;


final class Maven4Support implements MavenVersionAwareSupportExtension {
  private static final @NonNls String MAIN_CLASS40 = "com.intellij.maven.server.m40.RemoteMavenServer40";

  @Override
  public boolean isSupportedByExtension(@NotNull Path mavenHome) {
    String version = MavenUtil.getMavenVersion(mavenHome);
    return StringUtil.compareVersionNumbers(version, "4") >= 0;
  }

  @Override
  public @Nullable Path getMavenHomeFile(@Nullable StaticResolvedMavenHomeType mavenHomeType) {
    if (mavenHomeType == null) return null;
    if (mavenHomeType == BundledMaven4.INSTANCE) {
      return MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome();
    }
    return null;
  }

  @Override
  public @NotNull List<Path> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution) {
    final List<Path> classpath = new ArrayList<>();

    if (MavenUtil.isRunningFromSources()) {
      MavenLog.LOG.debug("collecting classpath for local run");
      prepareClassPathForLocalRunAndUnitTests(distribution.getVersion(), classpath);
    }
    else {
      MavenLog.LOG.debug("collecting classpath for production");
      prepareClassPathForProduction(classpath);
    }

    addMavenLibs(classpath, distribution.getMavenHome());
    MavenLog.LOG.debug("Collected classpath = ", classpath);
    return classpath;
  }

  private static void prepareClassPathForProduction(List<Path> classpath) {
    addMavenServerLibraries(classpath, "intellij.maven.server4");
  }

  private static void prepareClassPathForLocalRunAndUnitTests(@NotNull String mavenVersion, List<Path> classpath) {
    BuildDependenciesCommunityRoot communityRoot = new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()));
    BundledMavenDownloader.INSTANCE.downloadMaven4LibsSync(communityRoot);

    classpath.add(PathManager.getJarForClass(MavenId.class));
    classpath.add(locateModuleOutput("intellij.maven.server"));

    classpath.add(locateModuleOutput("intellij.maven.server.telemetry"));
    classpath.addAll(MavenUtil.collectClasspath(MavenServerTelemetryClasspathUtil.TELEMETRY_CLASSES));

    Path parentPath = MavenUtil.getMavenPluginParentFile();
    addDir(classpath, parentPath.resolve("maven40-server-impl/lib"), f -> true);

    classpath.add(locateModuleOutput("intellij.maven.server.m40"));
  }


  @Override
  public String getMainClass(MavenDistribution distribution) {
    return MAIN_CLASS40;
  }
}
