// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @deprecated use {@link com.jetbrains.python.packaging.management.PythonPackageManager}
 */
@Deprecated(forRemoval = true)
public abstract class PyPackageManager implements Disposable {
  public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

  public static final String USE_USER_SITE = "--user";
  @Topic.AppLevel
  public static final Topic<Listener> PACKAGE_MANAGER_TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * @param sdk must not be disposed if {@link Disposable}
   */
  public static @NotNull PyPackageManager getInstance(@NotNull Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  private final @NotNull Sdk mySdk;

  protected PyPackageManager(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  /**
   * @return if manager must be subscribed to SDK path changes
   */
  protected boolean shouldSubscribeToLocalChanges() {
    return true;
  }

  @ApiStatus.Internal
  public static boolean shouldSubscribeToLocalChanges(@NotNull PyPackageManager manager) {
    return manager.shouldSubscribeToLocalChanges();
  }

  protected final @NotNull Sdk getSdk() {
    return mySdk;
  }

  @ApiStatus.Internal
  public static @NotNull Sdk getSdk(@NotNull PyPackageManager manager) {
    return manager.getSdk();
  }

  public abstract void installManagement() throws ExecutionException;

  public abstract boolean hasManagement() throws ExecutionException;

  public abstract void install(@NotNull String requirementString) throws ExecutionException;

  public abstract void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException;

  public abstract void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException;

  public abstract void refresh();

  public abstract @NotNull String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException;

  public abstract @Nullable List<PyPackage> getPackages();

  public abstract @NotNull List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException;

  public abstract @Nullable List<PyRequirement> getRequirements(@NotNull Module module);

  /**
   * @param line requirement description
   * @return parsed requirement or null if given description could not be parsed.
   * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
   */
  public abstract @Nullable PyRequirement parseRequirement(@NotNull String line);

  /**
   * @param text requirements descriptions
   * @return parsed requirements.
   * <i>Note: the returned list does not contain null or repetitive values, descriptions that could not be parsed are skipped.</i>
   * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
   */
  public abstract @NotNull List<PyRequirement> parseRequirements(@NotNull String text);

  /**
   * @param file file containing requirements descriptions.
   *             Used as a foothold to resolve recursive requirements specified through <code>-r</code> or <code>--requirement</code> flags.
   * @return parsed requirements.
   * <i>Note: the returned list does not contain null or repetitive values, descriptions that could not be parsed are skipped.</i>
   * @see <a href="https://pip.pypa.io/en/stable/reference/pip_install/"><code>pip install</code> documentation</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0508/">PEP-508</a>
   * @see <a href="https://www.python.org/dev/peps/pep-0440/">PEP-440</a>
   */
  public abstract @NotNull List<PyRequirement> parseRequirements(@NotNull VirtualFile file);

  public abstract @NotNull Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException;

  public interface Listener {
    void packagesRefreshed(@NotNull Sdk sdk);
  }

  @Override
  public void dispose() {
  }
}
