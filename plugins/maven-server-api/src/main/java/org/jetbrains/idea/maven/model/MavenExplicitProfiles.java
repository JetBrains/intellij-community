// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class MavenExplicitProfiles implements Serializable {
  public static final MavenExplicitProfiles NONE = new MavenExplicitProfiles(Collections.emptySet());

  private final HashSet<String> myEnabledProfiles;
  private final HashSet<String> myDisabledProfiles;

  public MavenExplicitProfiles(@NotNull Collection<@NotNull String> enabledProfiles,
                               @NotNull Collection<@NotNull String> disabledProfiles) {
    myEnabledProfiles = new HashSet<>(enabledProfiles);
    myDisabledProfiles = new HashSet<>(disabledProfiles);
  }

  public MavenExplicitProfiles(Collection<String> enabledProfiles) {
    this(enabledProfiles, Collections.emptySet());
  }

  public @NotNull Collection<@NotNull String> getEnabledProfiles() {
    return myEnabledProfiles;
  }

  public @NotNull Collection<@NotNull String> getDisabledProfiles() {
    return myDisabledProfiles;
  }

  @Override
  public MavenExplicitProfiles clone() {
    return new MavenExplicitProfiles(myEnabledProfiles, myDisabledProfiles);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenExplicitProfiles that = (MavenExplicitProfiles)o;

    if (!myEnabledProfiles.equals(that.myEnabledProfiles)) return false;
    if (!myDisabledProfiles.equals(that.myDisabledProfiles)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myEnabledProfiles.hashCode();
    result = 31 * result + myDisabledProfiles.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "MavenExplicitProfiles{" +
           "myEnabledProfiles=" + myEnabledProfiles +
           ", myDisabledProfiles=" + myDisabledProfiles +
           '}';
  }
}
