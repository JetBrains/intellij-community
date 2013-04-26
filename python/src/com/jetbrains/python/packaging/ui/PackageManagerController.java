package com.jetbrains.python.packaging.ui;

import com.intellij.util.CatchingConsumer;
import com.jetbrains.python.packaging.RepoPackage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public interface PackageManagerController {
  List<RepoPackage> getAllPackages() throws IOException;

  void reloadPackagesList() throws IOException;

  void installPackage(String packageName, String repositoryUrl, @Nullable String version, boolean installToUser,
                      @Nullable String extraOptions, Listener listener);

  void fetchPackageVersions(String packageName, CatchingConsumer<List<String>, Exception> consumer);

  void fetchPackageDetails(String packageName, CatchingConsumer<String, Exception> consumer);

  interface Listener {
    void installationStarted();
    void installationFinished(@Nullable String errorDescription);
  }
}
