// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collection;
import java.util.function.Predicate;

public interface MavenServerManager extends Disposable {
  String BUNDLED_MAVEN_2 = "Bundled (Maven 2)";
  String BUNDLED_MAVEN_3 = "Bundled (Maven 3)";
  String WRAPPED_MAVEN = "Use Maven wrapper";

  Collection<MavenServerConnector> getAllConnectors();

  void restartMavenConnectors(Project project, boolean wait, Predicate<MavenServerConnector> condition);

  MavenServerConnector getConnector(@NotNull Project project, @NotNull String workingDirectory);

  boolean shutdownConnector(MavenServerConnector connector, boolean wait);

  void shutdown(boolean wait);

  File getMavenEventListener();

  @NotNull MavenEmbedderWrapper createEmbedder(Project project,
                                               boolean alwaysOnline,
                                               @Nullable String workingDirectory,
                                               @NotNull String multiModuleProjectDirectory);

  MavenIndexerWrapper createIndexer(@NotNull Project project);

  static MavenServerManager getInstance() {
    return ApplicationManager.getApplication().getService(MavenServerManager.class);
  }

  @Nullable
  static MavenServerManager getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(MavenServerManager.class);
  }

  static boolean verifyMavenSdkRequirements(@NotNull Sdk jdk, String mavenVersion) {
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3.1") < 0) {
      return true;
    }
    SdkTypeId sdkType = jdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      JavaSdkVersion version = ((JavaSdk)sdkType).getVersion(jdk);
      if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return true;
      }
    }
    return false;
  }

  static @Nullable String getMavenVersion(@Nullable String mavenHome) {
    return MavenUtil.getMavenVersion(getMavenHomeFile(mavenHome));
  }

  @Nullable
  default String getMavenVersion(@Nullable File mavenHome) {
    return MavenUtil.getMavenVersion(mavenHome);
  }

  /**
   * @deprecated use {@link MavenGeneralSettings.mavenHome} and {@link MavenUtil.getMavenVersion}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  default String getCurrentMavenVersion() {
    return null;
  }

  default boolean isUseMaven2() {
    return false;
  }

  /**
   * do not use this method directly, as it is impossible to resolve correct version if maven home is set to wrapper
   * @see MavenDistributionsCache
   */
  @Nullable
  @ApiStatus.Internal
  static File getMavenHomeFile(@Nullable String mavenHome) {
    if (mavenHome == null) return null;
    for (MavenVersionAwareSupportExtension e : MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.getExtensionList()) {
      File file = e.getMavenHomeFile(mavenHome);
      if (file != null) return file;
    }

    final File home = new File(mavenHome);
    return MavenUtil.isValidMavenHome(home) ? home : null;
  }

  @ApiStatus.Internal
  interface MavenServerConnectorFactory {
    @NotNull MavenServerConnector create(@NotNull Project project,
                                         @NotNull Sdk jdk,
                                         @NotNull String vmOptions,
                                         @Nullable Integer debugPort,
                                         @NotNull MavenDistribution mavenDistribution,
                                         @NotNull String multimoduleDirectory);
  }

  @ApiStatus.Internal
  class MavenServerConnectorFactoryImpl implements MavenServerConnectorFactory {

    @Override
    public @NotNull MavenServerConnector create(@NotNull Project project,
                                                @NotNull Sdk jdk,
                                                @NotNull String vmOptions,
                                                @Nullable Integer debugPort,
                                                @NotNull MavenDistribution mavenDistribution,
                                                @NotNull String multimoduleDirectory) {
      return new MavenServerConnectorImpl(project, jdk, vmOptions, debugPort, mavenDistribution, multimoduleDirectory);
    }
  }
}
