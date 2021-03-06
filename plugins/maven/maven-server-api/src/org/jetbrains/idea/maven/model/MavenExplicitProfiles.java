// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public class MavenExplicitProfiles implements Serializable {
  public static final MavenExplicitProfiles NONE = new MavenExplicitProfiles(Collections.<String>emptySet());

  private final Collection<String> myEnabledProfiles;
  private final Collection<String> myDisabledProfiles;

  public MavenExplicitProfiles(Collection<String> enabledProfiles, Collection<String> disabledProfiles) {
    myEnabledProfiles = enabledProfiles;
    myDisabledProfiles = disabledProfiles;
  }

  public MavenExplicitProfiles(Collection<String> enabledProfiles) {
    this(enabledProfiles, Collections.<String>emptySet());
  }

  public Collection<String> getEnabledProfiles() {
    return myEnabledProfiles;
  }

  public Collection<String> getDisabledProfiles() {
    return myDisabledProfiles;
  }

  @Override
  public MavenExplicitProfiles clone() {
    return new MavenExplicitProfiles(new HashSet<String>(myEnabledProfiles), new HashSet<String>(myDisabledProfiles));
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
}
