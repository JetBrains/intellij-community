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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyPackage aPackage = (PyPackage)o;
    if (myName != null ? !myName.equals(aPackage.myName) : aPackage.myName != null) return false;
    if (myVersion != null ? !myVersion.equals(aPackage.myVersion) : aPackage.myVersion != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }
}
