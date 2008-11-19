package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RepositoryLocationGroup implements RepositoryLocation {
  private final String myPresentableString;
  private final List<RepositoryLocation> myLocations;

  public RepositoryLocationGroup(final String presentableString) {
    myPresentableString = presentableString;
    myLocations = new ArrayList<RepositoryLocation>();
  }

  public String toPresentableString() {
    return myPresentableString;
  }

  public void add(@NotNull final RepositoryLocation location) {
    for (int i = 0; i < myLocations.size(); i++) {
      final RepositoryLocation t = myLocations.get(i);
      if (t.getKey().compareTo(location.getKey()) >= 0) {
        myLocations.add(i, location);
        return;
      }
    }
    myLocations.add(location);
  }

  public String getKey() {
    final StringBuilder sb = new StringBuilder(myPresentableString);
    // they are ordered
    for (RepositoryLocation location : myLocations) {
      sb.append(location.getKey());
    }
    return sb.toString();
  }

  public List<RepositoryLocation> getLocations() {
    return myLocations;
  }
}
