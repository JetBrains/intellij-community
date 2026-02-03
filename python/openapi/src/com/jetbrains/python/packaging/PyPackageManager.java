// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @deprecated use {@link com.jetbrains.python.packaging.management.PythonPackageManager}
 */
@Deprecated(forRemoval = true)
public abstract class PyPackageManager implements Disposable {

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

  public abstract void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException;

  public abstract void refresh();

  public abstract @NotNull String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException;

  public abstract @Nullable List<PyPackage> getPackages();

  public abstract @NotNull List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException;

  public interface Listener {
    void packagesRefreshed(@NotNull Sdk sdk);
  }

  @Override
  public void dispose() {
  }
}
