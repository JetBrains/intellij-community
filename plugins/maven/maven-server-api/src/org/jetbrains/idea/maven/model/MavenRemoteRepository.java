/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class MavenRemoteRepository implements Serializable {
  private final String myId;
  private final String myName;
  private final String myUrl;
  private final String myLayout;
  private final Policy myReleasesPolicy;
  private final Policy mySnapshotsPolicy;

  public MavenRemoteRepository(@NotNull String id,
                               @Nullable String name,
                               @NotNull String url,
                               @Nullable String layout,
                               @Nullable Policy releasesPolicy,
                               @Nullable Policy snapshotsPolicy) {
    myId = id;
    myName = name;
    myUrl = url;
    myLayout = layout;
    myReleasesPolicy = releasesPolicy;
    mySnapshotsPolicy = snapshotsPolicy;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @Nullable
  public String getLayout() {
    return myLayout;
  }

  @Nullable
  public Policy getReleasesPolicy() {
    return myReleasesPolicy;
  }

  @Nullable
  public Policy getSnapshotsPolicy() {
    return mySnapshotsPolicy;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenRemoteRepository that = (MavenRemoteRepository)o;

    if (myId != null ? !myId.equals(that.myId) : that.myId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId != null ? myId.hashCode() : 0;
  }

  public static class Policy implements Serializable {
    private final boolean myEnabled;
    private final String myUpdatePolicy;
    private final String myChecksumPolicy;

    public Policy(boolean enabled, String updatePolicy, String checksumPolicy) {
      myEnabled = enabled;
      myUpdatePolicy = updatePolicy;
      myChecksumPolicy = checksumPolicy;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public String getUpdatePolicy() {
      return myUpdatePolicy;
    }

    public String getChecksumPolicy() {
      return myChecksumPolicy;
    }
  }
}
