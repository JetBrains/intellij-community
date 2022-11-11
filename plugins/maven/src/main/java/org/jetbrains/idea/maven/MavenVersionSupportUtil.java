// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;

public class MavenVersionSupportUtil {
  public static final String MAVEN_2_PLUGIN_ID = "org.jetbrains.idea.maven.maven2-support";

  public static @Nullable MavenVersionAwareSupportExtension getExtensionFor(MavenDistribution distribution) {
    return MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.findFirstSafe(
      e -> e.isSupportedByExtension(distribution.getMavenHome().toFile()));
  }

  public static boolean isMaven2PluginInstalled() {
    return PluginManager.isPluginInstalled(PluginId.getId(MAVEN_2_PLUGIN_ID));
  }

  public static boolean isMaven2PluginDisabled() {
    return isMaven2PluginInstalled() && PluginManagerCore.isDisabled(PluginId.getId(MAVEN_2_PLUGIN_ID));
  }

  public static boolean isMaven2Used(@NotNull Project project) {
    String version = MavenDistributionsCache.getInstance(project).getSettingsDistribution().getVersion();
    if (version == null) return false;
    return StringUtil.compareVersionNumbers(version, "3") < 0;
  }
}
