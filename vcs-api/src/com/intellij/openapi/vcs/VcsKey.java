package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NotNull;

public final class VcsKey {
  @NotNull
  private final String myName;

  // to forbid creation outside AbstractVcs
  VcsKey(@NotNull final String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsKey vcsKey = (VcsKey)o;

    if (!myName.equals(vcsKey.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}
