package com.jetbrains.python.packaging;

import com.intellij.webcore.packaging.InstalledPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyPackage extends InstalledPackage {
  private final String myLocation;
  private final List<PyRequirement> myRequirements;

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location, @NotNull List<PyRequirement> requirements) {
    super(name, version);
    myLocation = location;
    myRequirements = requirements;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  public List<PyRequirement> getRequirements() {
    return myRequirements;
  }

  @Nullable
  public String getLocation() {
    return myLocation;
  }

  public boolean isInstalled() {
    return myLocation != null;
  }
}
