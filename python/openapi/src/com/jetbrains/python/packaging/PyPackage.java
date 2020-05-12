// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public final class PyPackage extends InstalledPackage {
  private final String myLocation;
  private final List<PyRequirement> myRequirements;

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location, @NotNull List<PyRequirement> requirements) {
    super(name, version);
    myLocation = location;
    myRequirements = requirements;
  }

  @NotNull
  public List<PyRequirement> getRequirements() {
    return myRequirements;
  }


  /**
   * Checks if package meets requirement, described in [PEP-0386] format using {@link PyRequirement}
   *
   * @param requirement to check if package matches
   * @return true if matches.
   */
  public boolean matches(@NotNull PyRequirement requirement) {
    return requirement.match(Collections.singletonList(this)) != null;
  }

  @Nullable
  public String getLocation() {
    return myLocation;
  }

  public boolean isInstalled() {
    return myLocation != null;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return FileUtil.getLocationRelativeToUserHome(myLocation);
  }
}
