// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public final class MavenRemoteRepository implements Serializable {
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

  public @NotNull String getId() {
    return myId;
  }

  public @Nullable String getName() {
    return myName;
  }

  public @NotNull String getUrl() {
    return myUrl;
  }

  public @Nullable String getLayout() {
    return myLayout;
  }

  public @Nullable Policy getReleasesPolicy() {
    return myReleasesPolicy;
  }

  public @Nullable Policy getSnapshotsPolicy() {
    return mySnapshotsPolicy;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenRemoteRepository that = (MavenRemoteRepository)o;

    if (!myId.equals(that.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
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
