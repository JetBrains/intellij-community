// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @author vlan
 */
public final class PyPackage extends InstalledPackage {
  private final @Nullable String myLocation;
  private final @NotNull List<PyRequirement> myRequirements;

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location, @NotNull List<PyRequirement> requirements) {
    super(name, version);
    myLocation = location;
    myRequirements = requirements;
  }

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location) {
    this(name, version, location, List.of());
  }

  public PyPackage(@NotNull String name, @NotNull String version) {
    this(name, version, null);
  }

  @Override
  public @NlsSafe @NotNull String getVersion() {
    return Objects.requireNonNull(super.getVersion());
  }

  public @NotNull List<PyRequirement> getRequirements() {
    return myRequirements;
  }

  /**
   * Checks if package meets requirement, described in [PEP-0386] format using {@link PyRequirement}
   *
   * @param requirement to check if package matches
   * @return true if matches.
   */
  public boolean matches(@NotNull PyRequirement requirement) {
    return requirement.match(List.of(this)) != null;
  }

  public @Nullable String getLocation() {
    return myLocation;
  }

  public boolean isInstalled() {
    return myLocation != null;
  }

  @Override
  public @Nullable String getTooltipText() {
    return FileUtil.getLocationRelativeToUserHome(myLocation);
  }
}
