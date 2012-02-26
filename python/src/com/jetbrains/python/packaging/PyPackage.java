package com.jetbrains.python.packaging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyPackage {
  private final String myName;
  private final String myVersion;
  private final String myLocation;
  private final List<PyRequirement> myRequirements;

  public PyPackage(@NotNull String name, @NotNull String version, @Nullable String location, @NotNull List<PyRequirement> requirements) {
    myName = name;
    myVersion = version;
    myLocation = location;
    myRequirements = requirements;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
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
