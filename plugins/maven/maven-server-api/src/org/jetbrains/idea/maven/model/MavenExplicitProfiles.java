/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.model;

import gnu.trove.THashSet;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

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
    return new MavenExplicitProfiles(new THashSet<String>(myEnabledProfiles), new THashSet<String>(myDisabledProfiles));
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
