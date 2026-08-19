package com.android.tools.adtui.webp;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil;

import java.nio.file.Path;

public final class WebpNativeLibDownloader {
  @TestOnly
  public static void ensureWebpRegistered() {
    var communityRoot = new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()));
    var propertiesFile = communityRoot.communityRoot.resolve("build/dependencies/dependencies.properties");
    var version = BuildDependenciesUtil.INSTANCE.loadPropertiesFile(propertiesFile).get("libwebpVersion");
    var uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL, "org.jetbrains.intellij.deps", "libwebp", version, "tar.gz"
    );
    var archiveFile = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri);
    var unpackedDir = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, archiveFile);
    var archName = switch (CpuArch.CURRENT) {
      case ARM64 -> "AArch64";
      case X86_64 -> "X86_64";
      default -> throw new IllegalStateException();
    };
    var libLocation = unpackedDir.resolve(OS.CURRENT.name() + '-' + archName).toString();

    System.setProperty(WebpMetadata.TEST_LIB_LOCATION, libLocation);
    try {
      WebpMetadata.ensureWebpRegistered();
    }
    finally {
      System.clearProperty(WebpMetadata.TEST_LIB_LOCATION);
    }
  }
}
