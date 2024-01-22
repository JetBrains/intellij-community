// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
@ApiStatus.Experimental
public interface MavenSyncSpec {
  static MavenSyncSpec incremental(String description) {
    return incremental(description, false);
  }

  static MavenSyncSpec incremental(String description, boolean explicit) {
    return new MavenSyncSpecImpl(true, explicit, description);
  }

  static MavenSyncSpec full(String description) {
    return full(description, false);
  }

  static MavenSyncSpec full(String description, boolean explicit) {
    return new MavenSyncSpecImpl(false, explicit, description);
  }

  boolean forceReading();

  boolean resolveIncrementally();

  boolean isExplicit();
}

class MavenSyncSpecImpl implements MavenSyncSpec {
  private final boolean incremental;
  private final boolean explicit;
  private final String description;

  MavenSyncSpecImpl(boolean incremental, boolean explicit, String description) {
    this.incremental = incremental;
    this.explicit = explicit;
    this.description = description;
  }

  @Override
  public boolean forceReading() {
    return !incremental;
  }

  @Override
  public boolean resolveIncrementally() {
    return incremental;
  }

  @Override
  public boolean isExplicit() {
    return explicit;
  }

  @Override
  public String toString() {
    return "incremental=" + incremental + ", " + description;
  }
}
